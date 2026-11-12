package com.dth.frw.module.school.fee.notification.service;

import com.dth.frw.domain.school.fee.FeeCollectionBatch;
import com.dth.frw.domain.school.fee.FeeNotification;
import com.dth.frw.domain.school.fee.FeeNotificationRecipientStatus;
import com.dth.frw.domain.school.fee.FeeNotificationStatus;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchLock;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchService;
import com.dth.frw.module.school.fee.notification.repo.FeeNotificationRepository;
import com.dth.frw.module.school.fee.notification.repo.FeeNotificationSpecifications;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.Candidate;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.CandidatePage;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.CollectStatus;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.Created;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.FeeLine;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.IdAmount;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.MailContext;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.Mode;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.NotifyStatus;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.Recipient;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.RecipientPage;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.Rendered;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.Requeued;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.Tree;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.TreeClass;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.TreeGroup;
import com.dth.frw.shared.exception.ConflictException;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import com.dth.frw.shared.exception.ValidationFailedException;
import com.dth.frw.shared.security.CurrentUser;
import com.dth.frw.shared.security.rowlevel.Action;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.dth.frw.shared.web.ListParams;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Thông báo thu tiền: telling the parents of a đợt thu's students what they owe.
 *
 * <p>The rules are the ones the Jmix application's notification service held, re-cut for this
 * one. A notification belongs to a đợt thu and goes to students picked from it. What each is
 * told is frozen when the notification is made — name, class, the guardian's address and the
 * khoản thu still owed — so what was sent is what is on record, and each student is given a
 * payment request whose order code goes in the bank transfer. A student who is already told
 * (sent, or waiting in a notification that has been scheduled) is not picked twice; one whose
 * mail failed can be picked again; one who owes nothing is left out.
 *
 * <p>A notification is a draft until it is sent or scheduled. From then on it has promised
 * something, so the đợt thu it belongs to is locked ({@link FeeCollectionBatchLock}) and the
 * notification can only be cancelled while it has not started. The sending itself is
 * {@link FeeNotificationDispatcher}'s: this class only writes down what is to be sent.
 *
 * <p>Recipients and payment requests are written in SQL — a school-wide notification is
 * thousands of rows, and an identity key forbids batching them through the persistence
 * context — and the counters on the notification are recomputed from them rather than
 * adjusted, exactly as the đợt thu's totals are.
 */
@Service
@Transactional(readOnly = true)
public class FeeNotificationService implements FeeCollectionBatchLock {

    private static final Set<String> SORTABLE = Set.of("id", "subject", "status", "sendAt", "createdAt",
            "recipientCount", "sentCount", "failedCount");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private static final int MAX_RECIPIENTS = 10_000;
    private static final int CHUNK = 1_000;
    private static final String RESOURCE = "thông báo";
    private static final String ORDER_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int ORDER_SUFFIX_LENGTH = 14;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /**
     * Every student of a đợt with what they owe and what they have been told. The {@code
     * notify_status} reads the recipient rows of notifications that are not drafts, most
     * final first: told beats waiting beats failed beats "no address".
     */
    private static final String CANDIDATES = """
            select bs.id as bs_id, st.full_name, st.student_code, st.guardian_name, st.guardian_email,
                   cg.id as group_id, cg.name as group_name, sc.id as class_id, sc.name as class_name,
                   bs.receivable_amount, bs.collected_amount,
                   greatest(bs.receivable_amount - bs.collected_amount, 0) as outstanding,
                   case when bs.receivable_amount <= 0 then 'NO_DEBT'
                        when bs.collected_amount <= 0 then 'UNPAID'
                        when bs.collected_amount >= bs.receivable_amount then 'PAID'
                        else 'PARTIAL' end as collect_status,
                   case
                     when exists (select 1 from fee_notification_recipients r
                                  join fee_notifications n on n.id = r.notification_id
                                  where r.batch_student_id = bs.id and n.status <> 'DRAFT'
                                    and r.status = 'SENT') then 'SENT'
                     when exists (select 1 from fee_notification_recipients r
                                  join fee_notifications n on n.id = r.notification_id
                                  where r.batch_student_id = bs.id and n.status in ('SCHEDULED', 'SENDING')
                                    and r.status = 'PENDING') then 'QUEUED'
                     when exists (select 1 from fee_notification_recipients r
                                  join fee_notifications n on n.id = r.notification_id
                                  where r.batch_student_id = bs.id and n.status <> 'DRAFT'
                                    and r.status = 'FAILED') then 'FAILED'
                     when exists (select 1 from fee_notification_recipients r
                                  join fee_notifications n on n.id = r.notification_id
                                  where r.batch_student_id = bs.id and n.status <> 'DRAFT'
                                    and r.status = 'MISSING_EMAIL') then 'MISSING_EMAIL'
                     else 'NONE' end as notify_status
            from fee_collection_batch_students bs
            join student_enrollments en on en.id = bs.enrollment_id
            join students st on st.id = en.student_id
            join class_groups cg on cg.id = bs.class_group_id
            join school_classes sc on sc.id = bs.school_class_id
            where bs.batch_id = :batchId
            """;

    private final FeeNotificationRepository repository;
    private final FeeCollectionBatchService batchService;
    private final RowLevelSecurity rowLevel;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final FeeNotificationEmailRenderer renderer;
    private final FeeNotificationProperties properties;
    private final FeePaymentProperties paymentProperties;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public FeeNotificationService(FeeNotificationRepository repository,
                                  FeeCollectionBatchService batchService,
                                  RowLevelSecurity rowLevel,
                                  JdbcTemplate jdbc,
                                  NamedParameterJdbcTemplate named,
                                  FeeNotificationEmailRenderer renderer,
                                  FeeNotificationProperties properties,
                                  FeePaymentProperties paymentProperties,
                                  ObjectMapper mapper) {
        this.repository = repository;
        this.batchService = batchService;
        this.rowLevel = rowLevel;
        this.jdbc = jdbc;
        this.named = named;
        this.renderer = renderer;
        this.properties = properties;
        this.paymentProperties = paymentProperties;
        this.mapper = mapper;
    }

    // ---- lock on the đợt thu

    @Override
    public boolean locksChanges(Long batchId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from fee_notifications where batch_id = ? and status <> 'DRAFT')",
                Boolean.class, batchId));
    }

    @Override
    public boolean hasNotifications(Long batchId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from fee_notifications where batch_id = ?)", Boolean.class, batchId));
    }

    // ---- reads

    public Page<FeeNotification> list(ListParams params) {
        return repository.findAll(
                rowLevel.scope(FeeNotification.class, Action.READ).and(FeeNotificationSpecifications.from(params)),
                params.pageable(SORTABLE, DEFAULT_SORT));
    }

    public FeeNotification get(Long id) {
        return repository.findOne(rowLevel.byId(FeeNotification.class, Action.READ, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    /** Who a notification went to, newest problems first, with how many there are of each status. */
    public RecipientPage recipients(Long notificationId, ListParams params) {
        get(notificationId);
        MapSqlParameterSource args = new MapSqlParameterSource("id", notificationId);
        StringBuilder where = new StringBuilder(" where r.notification_id = :id");
        for (ListParams.Criterion criterion : params.filters()) {
            if (criterion.field().equals("status")) {
                String value = criterion.value().trim().toUpperCase(Locale.ROOT);
                if (!value.equals("ANY")) {
                    where.append(" and r.status = :status");
                    args.addValue("status", value);
                }
            }
        }
        params.search().ifPresent(term -> {
            where.append(" and (lower(r.student_name) like :q or lower(r.student_code) like :q)");
            args.addValue("q", ListParams.likePattern(term));
        });
        long total = named.queryForObject("select count(*) from fee_notification_recipients r" + where, args, Long.class);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (FeeNotificationRecipientStatus status : FeeNotificationRecipientStatus.values()) {
            counts.put(status.name(), 0L);
        }
        jdbc.query("select status, count(*) from fee_notification_recipients where notification_id = ? group by status",
                rs -> {
                    counts.put(rs.getString(1), rs.getLong(2));
                }, notificationId);

        int[] range = pageOf(params);
        args.addValue("limit", range[1]).addValue("offset", range[0]);
        List<Recipient> rows = named.query("""
                select r.id, r.batch_student_id, r.student_name, r.student_code, r.class_group_name, r.class_name,
                       r.guardian_name, r.email, r.total_amount, r.status, r.attempts, r.sent_at, r.error_message,
                       p.order_code
                from fee_notification_recipients r
                left join fee_payment_requests p on p.id = r.payment_request_id
                """ + where + """
                 order by case r.status when 'FAILED' then 0 when 'MISSING_EMAIL' then 1 when 'PENDING' then 2 else 3 end,
                          r.class_name, r.student_name, r.id
                 limit :limit offset :offset
                """, args, (rs, i) -> new Recipient(
                rs.getLong("id"), rs.getLong("batch_student_id"), rs.getString("student_name"),
                rs.getString("student_code"), rs.getString("class_group_name"), rs.getString("class_name"),
                rs.getString("guardian_name"), rs.getString("email"), rs.getBigDecimal("total_amount"),
                rs.getString("status"), rs.getInt("attempts"),
                rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(),
                rs.getString("error_message"), rs.getString("order_code")));
        return new RecipientPage(rows, total, counts);
    }

    // ---- who a notification can go to

    /**
     * A page of the students of a đợt with what they owe and what they have been told. Filters:
     * {@code mode} ({@code ALL}, {@code UNNOTIFIED}, {@code DEBT}), {@code classGroupId},
     * {@code schoolClassId}, {@code collect} ({@code unpaid}, {@code partial}, {@code paid}),
     * {@code notify} ({@code none}, {@code queued}, {@code sent}, {@code failed}) and {@code q}.
     */
    public CandidatePage candidates(Long batchId, ListParams params, Mode mode) {
        batchService.get(batchId);
        Where w = candidateWhere(batchId, params, mode, true);
        Map<String, Object> totals = named.queryForMap("select count(*) as n, coalesce(sum(outstanding), 0) as o,"
                + " coalesce(sum(receivable_amount), 0) as r from (" + CANDIDATES + ") t" + w.sql(), w.args());
        int[] range = pageOf(params);
        w.args().addValue("limit", range[1]).addValue("offset", range[0]);
        List<Candidate> rows = named.query("select * from (" + CANDIDATES + ") t" + w.sql()
                + " order by class_name, full_name, bs_id limit :limit offset :offset", w.args(),
                (rs, i) -> candidate(rs));
        return new CandidatePage(rows, ((Number) totals.get("n")).longValue(),
                (BigDecimal) totals.get("o"), (BigDecimal) totals.get("r"));
    }

    /** Every student the filters reach, as ids, so one click can select them all. */
    public List<IdAmount> candidateIds(Long batchId, ListParams params, Mode mode) {
        batchService.get(batchId);
        Where w = candidateWhere(batchId, params, mode, true);
        w.args().addValue("limit", MAX_RECIPIENTS);
        return named.query("select bs_id, outstanding from (" + CANDIDATES + ") t" + w.sql()
                + " order by class_name, full_name, bs_id limit :limit", w.args(),
                (rs, i) -> new IdAmount(rs.getLong(1), rs.getBigDecimal(2)));
    }

    /** Khối and lớp with how many students each has under the current mode. */
    public Tree tree(Long batchId, ListParams params, Mode mode) {
        batchService.get(batchId);
        Where w = candidateWhere(batchId, params, mode, false);
        Map<Long, TreeGroup> groups = new LinkedHashMap<>();
        Map<Long, List<TreeClass>> classes = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        int[] total = {0};
        named.query("select group_id, group_name, class_id, class_name, count(*) as n from (" + CANDIDATES + ") t"
                + w.sql() + " group by group_id, group_name, class_id, class_name order by group_name, class_name",
                w.args(), rs -> {
                    long groupId = rs.getLong("group_id");
                    int n = rs.getInt("n");
                    names.putIfAbsent(groupId, rs.getString("group_name"));
                    counts.merge(groupId, n, Integer::sum);
                    classes.computeIfAbsent(groupId, k -> new ArrayList<>())
                            .add(new TreeClass(rs.getLong("class_id"), rs.getString("class_name"), n));
                    total[0] += n;
                });
        for (Map.Entry<Long, String> e : names.entrySet()) {
            groups.put(e.getKey(), new TreeGroup(e.getKey(), e.getValue(), counts.get(e.getKey()),
                    classes.get(e.getKey())));
        }
        return new Tree(total[0], List.copyOf(groups.values()));
    }

    // ---- preview

    /** The e-mail one student would get, rendered, without writing anything. */
    public Rendered preview(Long batchId, Long batchStudentId, String subject, String content, LocalDate deadline) {
        FeeCollectionBatch batch = batchService.get(batchId);
        requirePaymentConfigured();
        Snapshot snapshot = loadSnapshots(batchId, List.of(batchStudentId)).get(batchStudentId);
        if (snapshot == null) {
            throw new ResourceNotFoundException("học sinh trong đợt thu", batchStudentId);
        }
        String orderCode = sampleOrderCode(batchStudentId);
        LocalDate due = deadline != null ? deadline : LocalDate.now(properties.zone()).plusDays(14);
        MailContext context = context(batch, snapshot, orEmpty(subject), orEmpty(content), due, orderCode,
                snapshot.total(), paymentSnapshot());
        return new Rendered(context.subject(), renderer.render(context, batch.getOrganization().getLogo()),
                snapshot.studentName(), snapshot.className(), snapshot.email());
    }

    /** The subject and text a new notification starts from, so the form opens ready. */
    public Map<String, Object> defaults(Long batchId) {
        FeeCollectionBatch batch = batchService.get(batchId);
        int month = batch.getCollectionMonth();
        LocalDate start = batch.getAcademicYear().getStartDate();
        int year = LocalDate.now(properties.zone()).getYear();
        if (start != null) {
            year = month >= start.getMonthValue() ? start.getYear() : start.getYear() + 1;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject", "Thông báo thu tiền tháng %02d/%d".formatted(month, year));
        out.put("content", "Nhà trường xin thông báo các khoản thu của học sinh trong kỳ này.\n"
                + "Quý phụ huynh vui lòng hoàn thành trước thời hạn bên dưới.\n\nTrân trọng.");
        out.put("deadline", LocalDate.now(properties.zone()).plusDays(14));
        out.put("paymentConfigured", paymentProperties.configured());
        out.put("senderUnit", properties.senderUnit());
        return out;
    }

    // ---- writes

    /**
     * Makes a notification for the students picked. As a draft nothing is promised; otherwise it
     * is scheduled for {@code sendAt} (now when empty) and the dispatcher takes it from there.
     * Students already told, and students who owe nothing, are left out — and counted, so the
     * screen can say so.
     */
    @Transactional
    public Created create(Long batchId, List<Long> batchStudentIds, String rawSubject, String rawContent,
                          LocalDate deadline, Instant sendAt, boolean draft) {
        FeeCollectionBatch batch = batchService.get(batchId);
        String subject = rawSubject == null ? "" : rawSubject.trim();
        String content = rawContent == null ? "" : rawContent.strip();
        validateText(subject, content, deadline);
        Instant when = draft ? null : resolveSendAt(sendAt);
        requirePaymentConfigured();

        Set<Long> wanted = new LinkedHashSet<>(batchStudentIds == null ? List.of() : batchStudentIds);
        if (wanted.isEmpty()) {
            throw invalid("recipients", "Chọn ít nhất một học sinh để thông báo");
        }
        if (wanted.size() > MAX_RECIPIENTS) {
            throw invalid("recipients", "Mỗi đợt thông báo tối đa " + MAX_RECIPIENTS + " học sinh");
        }

        // Two people picking students of the same đợt at once must not both tell the same student.
        jdbc.queryForObject("select id from fee_collection_batches where id = ? for update", Long.class, batchId);

        List<Long> ids = List.copyOf(wanted);
        Map<Long, NotifyStatus> told = notifyStatuses(batchId, ids);
        Map<Long, Snapshot> snapshots = loadSnapshots(batchId, ids);
        if (snapshots.size() != ids.size()) {
            throw invalid("recipients", "Có học sinh không thuộc đợt thu này");
        }

        int alreadyNotified = 0;
        int noDebt = 0;
        List<Snapshot> picked = new ArrayList<>();
        for (Long id : ids) {
            Snapshot s = snapshots.get(id);
            NotifyStatus status = told.getOrDefault(id, NotifyStatus.NONE);
            if (status == NotifyStatus.SENT || status == NotifyStatus.QUEUED) {
                alreadyNotified++;
            } else if (s.total().signum() <= 0) {
                noDebt++;
            } else {
                picked.add(s);
            }
        }
        if (picked.isEmpty()) {
            throw new ConflictException(alreadyNotified > 0
                    ? "Các học sinh đã chọn đều đã được thông báo"
                    : "Các học sinh đã chọn không còn khoản nào phải thu");
        }

        FeeNotification notification = new FeeNotification(batch, subject, content, deadline,
                CurrentUser.username().orElse(null));
        notification.setStatus(draft ? FeeNotificationStatus.DRAFT : FeeNotificationStatus.SCHEDULED);
        notification.setSendAt(when);
        repository.persist(notification);
        // Flushed first: the identity key only exists afterwards, and checking a policy against a
        // half-built row is wrong in every case.
        repository.flush();
        rowLevel.checkWritable(notification);

        insertRecipients(notification, picked, draft);
        refreshCounts(notification.getId());
        return new Created(get(notification.getId()), ids.size(), alreadyNotified, noDebt);
    }

    /** Changes the words of a draft. Who it goes to is fixed once it is made. */
    @Transactional
    public FeeNotification updateDraft(Long id, String rawSubject, String rawContent, LocalDate deadline) {
        FeeNotification notification = writable(id);
        requireStatus(notification, FeeNotificationStatus.DRAFT, "Chỉ sửa được thông báo đang là bản nháp");
        String subject = rawSubject == null ? "" : rawSubject.trim();
        String content = rawContent == null ? "" : rawContent.strip();
        validateText(subject, content, deadline);
        notification.setSubject(subject);
        notification.setContent(content);
        notification.setDeadline(deadline);
        repository.flush();
        rowLevel.checkWritable(notification);
        return notification;
    }

    /** Sends a draft: its payment requests become payable and the dispatcher is told when. */
    @Transactional
    public FeeNotification send(Long id, Instant sendAt) {
        FeeNotification notification = writable(id);
        requireStatus(notification, FeeNotificationStatus.DRAFT, "Thông báo này không còn là bản nháp");
        Instant when = resolveSendAt(sendAt);
        requirePaymentConfigured();
        jdbc.queryForObject("select id from fee_collection_batches where id = ? for update", Long.class,
                notification.getBatch().getId());

        // Anyone told in the meantime by another notification is taken out rather than told twice.
        jdbc.update("""
                delete from fee_payment_requests where id in (
                    select r.payment_request_id from fee_notification_recipients r
                    where r.notification_id = ? and r.payment_request_id is not null
                      and exists (select 1 from fee_notification_recipients o
                                  join fee_notifications n on n.id = o.notification_id
                                  where o.batch_student_id = r.batch_student_id and o.notification_id <> r.notification_id
                                    and n.status <> 'DRAFT'
                                    and (o.status = 'SENT' or (o.status = 'PENDING' and n.status in ('SCHEDULED', 'SENDING')))))
                """, id);
        refreshCounts(id);
        Long pending = jdbc.queryForObject(
                "select count(*) from fee_notification_recipients where notification_id = ? and status = 'PENDING'",
                Long.class, id);
        if (pending == null || pending == 0) {
            throw new ConflictException("Không còn học sinh nào có email để gửi");
        }
        jdbc.update("""
                update fee_payment_requests set status = 'PENDING', deadline = ?
                where status = 'DRAFT' and id in (select payment_request_id from fee_notification_recipients where notification_id = ?)
                """, java.sql.Date.valueOf(notification.getDeadline()), id);
        notification.setStatus(FeeNotificationStatus.SCHEDULED);
        notification.setSendAt(when);
        repository.flush();
        rowLevel.checkWritable(notification);
        return get(id);
    }

    /**
     * Puts the recipients whose mail failed (or had no address) back in the queue. An address
     * that was missing is read again from the student, so fixing it and retrying is enough.
     */
    @Transactional
    public Requeued retry(Long id, List<Long> recipientIds) {
        FeeNotification notification = writable(id);
        if (!notification.getStatus().canRetry()) {
            throw new ConflictException("Chỉ gửi lại được khi đợt thông báo đã gửi xong");
        }
        requirePaymentConfigured();
        MapSqlParameterSource args = new MapSqlParameterSource("id", id);
        String only = "";
        if (recipientIds != null && !recipientIds.isEmpty()) {
            only = " and r.id in (:ids)";
            args.addValue("ids", recipientIds);
        }
        // The address is read again: a recipient who had none, or whose address was wrong, may have been fixed.
        int requeued = named.update("""
                update fee_notification_recipients r
                set email = nullif(trim(st.guardian_email), ''),
                    status = case when nullif(trim(st.guardian_email), '') is null then 'MISSING_EMAIL' else 'PENDING' end,
                    error_message = null
                from fee_collection_batch_students bs
                join student_enrollments en on en.id = bs.enrollment_id
                join students st on st.id = en.student_id
                where bs.id = r.batch_student_id and r.notification_id = :id
                  and r.status in ('FAILED', 'MISSING_EMAIL')
                """ + only, args);
        Long stillMissing = jdbc.queryForObject(
                "select count(*) from fee_notification_recipients where notification_id = ? and status = 'MISSING_EMAIL'",
                Long.class, id);
        Long pending = jdbc.queryForObject(
                "select count(*) from fee_notification_recipients where notification_id = ? and status = 'PENDING'",
                Long.class, id);
        if (pending == null || pending == 0) {
            refreshCounts(id);
            throw new ConflictException("Không có học sinh nào có thể gửi lại");
        }
        notification.setStatus(FeeNotificationStatus.SCHEDULED);
        notification.setSendAt(Instant.now());
        repository.flush();
        refreshCounts(id);
        return new Requeued(requeued, stillMissing == null ? 0 : stillMissing.intValue());
    }

    /** Cancels a notification that has not started: nothing was sent, so nothing needs undoing. */
    @Transactional
    public FeeNotification cancel(Long id) {
        FeeNotification notification = repository.findOne(rowLevel.byId(FeeNotification.class, Action.DELETE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        if (!notification.getStatus().canBeCancelled()) {
            throw new ConflictException("Thông báo đã bắt đầu gửi nên không thể hủy");
        }
        // The recipients go with their payment requests (cascade), then the notification itself.
        jdbc.update("""
                delete from fee_payment_requests where id in
                    (select payment_request_id from fee_notification_recipients where notification_id = ?
                     and payment_request_id is not null)
                """, id);
        repository.remove(notification);
        return notification;
    }

    // ---- counters

    /** Rewrites the counters from the recipient rows. Called after anything that moves one. */
    @Transactional
    public void refreshCounts(Long notificationId) {
        jdbc.update("""
                update fee_notifications n set
                  recipient_count = c.total, sent_count = c.sent, failed_count = c.failed,
                  missing_email_count = c.missing, updated_at = now()
                from (select count(*) as total,
                             count(*) filter (where status = 'SENT') as sent,
                             count(*) filter (where status = 'FAILED') as failed,
                             count(*) filter (where status = 'MISSING_EMAIL') as missing
                      from fee_notification_recipients where notification_id = ?) c
                where n.id = ?
                """, notificationId, notificationId);
    }

    // ---- recipients

    private void insertRecipients(FeeNotification notification, List<Snapshot> picked, boolean draft) {
        int n = picked.size();
        List<Long> requestIds = jdbc.queryForList(
                "select nextval(pg_get_serial_sequence('fee_payment_requests', 'id')) from generate_series(1, ?)",
                Long.class, n);
        PaymentSnapshot bank = paymentSnapshot();
        String requestStatus = draft ? "DRAFT" : "PENDING";

        List<Object[]> requests = new ArrayList<>(n);
        List<Object[]> recipients = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Snapshot s = picked.get(i);
            Long requestId = requestIds.get(i);
            requests.add(new Object[] {requestId, notification.getOrganization().getId(),
                    notification.getAcademicYear().getId(), s.batchStudentId(), newOrderCode(), s.total(),
                    bank.number(), bank.bank(), bank.holder(), bank.baseUrl(), bank.template(), bank.showInfo(),
                    requestStatus, draft ? null : java.sql.Date.valueOf(notification.getDeadline())});
            boolean hasEmail = s.email() != null;
            recipients.add(new Object[] {notification.getId(), s.batchStudentId(), requestId, s.studentName(),
                    s.studentCode(), s.classGroupName(), s.className(), s.guardianName(), s.email(),
                    linesJson(s.lines()), s.total(),
                    (hasEmail ? FeeNotificationRecipientStatus.PENDING : FeeNotificationRecipientStatus.MISSING_EMAIL).name()});
        }
        jdbc.batchUpdate("""
                insert into fee_payment_requests (id, organization_id, academic_year_id, batch_student_id, order_code,
                    requested_amount, bank_account_number, bank_code, bank_account_holder, viet_qr_base_url,
                    viet_qr_template, viet_qr_show_info, status, deadline)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, requests);
        jdbc.batchUpdate("""
                insert into fee_notification_recipients (notification_id, batch_student_id, payment_request_id,
                    student_name, student_code, class_group_name, class_name, guardian_name, email, fee_lines_json,
                    total_amount, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, recipients);
    }

    private String newOrderCode() {
        return orderCode(random);
    }

    /** A code of the shape real ones have, from a generator that is the same every time it is seeded. */
    private String sampleOrderCode(long seed) {
        return orderCode(new java.util.Random(seed));
    }

    private String orderCode(java.util.Random source) {
        StringBuilder code = new StringBuilder(paymentProperties.orderCodePrefix().toUpperCase(Locale.ROOT)).append('-');
        for (int i = 0; i < ORDER_SUFFIX_LENGTH; i++) {
            code.append(ORDER_ALPHABET.charAt(source.nextInt(ORDER_ALPHABET.length())));
        }
        return code.toString();
    }

    private String linesJson(List<FeeLine> lines) {
        try {
            return mapper.writeValueAsString(lines);
        } catch (JacksonException e) {
            throw new IllegalStateException("Không ghi được các khoản thu của học sinh", e);
        }
    }

    public List<FeeLine> readLines(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<FeeLine>>() {
            });
        } catch (JacksonException e) {
            throw new IllegalStateException("Không đọc được các khoản thu của học sinh", e);
        }
    }

    // ---- loading what a student owes

    /** What each of these students owes and who to tell, frozen at this moment. */
    private Map<Long, Snapshot> loadSnapshots(Long batchId, List<Long> ids) {
        Map<Long, Snapshot> out = new LinkedHashMap<>();
        for (int from = 0; from < ids.size(); from += CHUNK) {
            List<Long> chunk = ids.subList(from, Math.min(from + CHUNK, ids.size()));
            Map<Long, List<FeeLine>> lines = new HashMap<>();
            named.query("""
                    select r.batch_student_id, i.name, r.receivable_amount - r.collected_amount as amount
                    from student_fee_receivables r
                    join fee_collection_batch_items i on i.id = r.batch_item_id
                    where r.batch_student_id in (:ids) and r.receivable_amount > r.collected_amount
                    order by r.batch_student_id, i.sequence
                    """, new MapSqlParameterSource("ids", chunk), rs -> {
                lines.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                        .add(new FeeLine(rs.getString(2), rs.getBigDecimal(3)));
            });
            named.query("select * from (" + CANDIDATES + " and bs.id in (:ids)) t",
                    new MapSqlParameterSource("batchId", batchId).addValue("ids", chunk), rs -> {
                        long id = rs.getLong("bs_id");
                        List<FeeLine> own = lines.getOrDefault(id, List.of());
                        BigDecimal total = own.stream().map(FeeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                        out.put(id, new Snapshot(id, rs.getString("full_name"), rs.getString("student_code"),
                                rs.getString("group_name"), rs.getString("class_name"),
                                rs.getString("guardian_name"), cleanEmail(rs.getString("guardian_email")), own, total));
                    });
        }
        return out;
    }

    private Map<Long, NotifyStatus> notifyStatuses(Long batchId, List<Long> ids) {
        Map<Long, NotifyStatus> out = new HashMap<>();
        for (int from = 0; from < ids.size(); from += CHUNK) {
            List<Long> chunk = ids.subList(from, Math.min(from + CHUNK, ids.size()));
            named.query("select bs_id, notify_status from (" + CANDIDATES + " and bs.id in (:ids)) t",
                    new MapSqlParameterSource("batchId", batchId).addValue("ids", chunk), rs -> {
                        out.put(rs.getLong(1), NotifyStatus.valueOf(rs.getString(2)));
                    });
        }
        return out;
    }

    private MailContext context(FeeCollectionBatch batch, Snapshot s, String subject, String content, LocalDate deadline,
                                String orderCode, BigDecimal amount, PaymentSnapshot bank) {
        return new MailContext(batch.getOrganization().getName(), properties.senderUnit(), subject, content, deadline,
                s.studentName(), s.studentCode(), s.className(), s.lines(), amount, orderCode,
                bank.number(), bank.bank(), bank.holder(), bank.baseUrl(), bank.template(), bank.showInfo());
    }

    // ---- small things

    private PaymentSnapshot paymentSnapshot() {
        return new PaymentSnapshot(paymentProperties.bankAccount().number(), paymentProperties.bankAccount().bank(),
                paymentProperties.bankAccount().holder(), paymentProperties.vietQr().baseUrl(),
                paymentProperties.vietQr().template(), paymentProperties.vietQr().showInfo());
    }

    private void requirePaymentConfigured() {
        if (!paymentProperties.configured()) {
            throw new ConflictException("Chưa cấu hình tài khoản nhận tiền (school-fee.payment.*) nên chưa lập được thông báo");
        }
    }

    private FeeNotification writable(Long id) {
        return repository.findOne(rowLevel.byId(FeeNotification.class, Action.WRITE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    private static void requireStatus(FeeNotification notification, FeeNotificationStatus expected, String message) {
        if (notification.getStatus() != expected) {
            throw new ConflictException(message);
        }
    }

    private void validateText(String subject, String content, LocalDate deadline) {
        Map<String, Object> errors = new LinkedHashMap<>();
        if (subject.isBlank()) {
            errors.put("subject", "Nhập tiêu đề");
        } else if (subject.length() > 500) {
            errors.put("subject", "Tiêu đề tối đa 500 ký tự");
        }
        if (content.isBlank()) {
            errors.put("content", "Nhập nội dung");
        } else if (content.length() > 5_000) {
            errors.put("content", "Nội dung tối đa 5.000 ký tự");
        }
        if (deadline == null) {
            errors.put("deadline", "Chọn hạn đóng");
        } else if (deadline.isBefore(LocalDate.now(properties.zone()))) {
            errors.put("deadline", "Hạn đóng không được ở quá khứ");
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException("Dữ liệu không hợp lệ", errors);
        }
    }

    private Instant resolveSendAt(Instant sendAt) {
        Instant now = Instant.now();
        if (sendAt == null) {
            return now;
        }
        // A minute of grace: the form fills "now" in when it opens and the person takes time to press the button.
        if (sendAt.isBefore(now.minusSeconds(120))) {
            throw invalid("sendAt", "Thời điểm gửi không được ở quá khứ");
        }
        return sendAt.isBefore(now) ? now : sendAt;
    }

    private static String cleanEmail(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return EMAIL.matcher(trimmed).matches() ? trimmed : null;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int[] pageOf(ListParams params) {
        var pageable = params.pageable(Set.of(), Sort.unsorted());
        return new int[] {(int) pageable.getOffset(), pageable.getPageSize()};
    }

    private static ValidationFailedException invalid(String field, String message) {
        return new ValidationFailedException("Dữ liệu không hợp lệ", Map.of(field, message));
    }

    private static Candidate candidate(java.sql.ResultSet rs) throws java.sql.SQLException {
        String email = cleanEmail(rs.getString("guardian_email"));
        return new Candidate(rs.getLong("bs_id"), rs.getString("full_name"), rs.getString("student_code"),
                rs.getLong("group_id"), rs.getString("group_name"), rs.getLong("class_id"), rs.getString("class_name"),
                rs.getBigDecimal("receivable_amount"), rs.getBigDecimal("collected_amount"),
                rs.getBigDecimal("outstanding"), CollectStatus.valueOf(rs.getString("collect_status")),
                NotifyStatus.valueOf(rs.getString("notify_status")), email != null);
    }

    private record Where(String sql, MapSqlParameterSource args) {
    }

    /**
     * The outer {@code where} over {@link #CANDIDATES}. {@code withPlace} is false for the tree,
     * which must keep showing every khối and lớp whichever one is selected.
     */
    private Where candidateWhere(Long batchId, ListParams params, Mode mode, boolean withPlace) {
        MapSqlParameterSource args = new MapSqlParameterSource("batchId", batchId);
        StringBuilder sql = new StringBuilder(" where 1 = 1");
        if (mode == Mode.UNNOTIFIED) {
            sql.append(" and notify_status in ('NONE', 'FAILED', 'MISSING_EMAIL')");
        } else if (mode == Mode.DEBT) {
            sql.append(" and outstanding > 0");
        }
        for (ListParams.Criterion criterion : params.filters()) {
            String value = criterion.value().trim();
            switch (criterion.field()) {
                case "classGroupId" -> {
                    if (withPlace) {
                        sql.append(" and group_id = :groupId");
                        args.addValue("groupId", parseLong(value));
                    }
                }
                case "schoolClassId" -> {
                    if (withPlace) {
                        sql.append(" and class_id = :classId");
                        args.addValue("classId", parseLong(value));
                    }
                }
                case "owing" -> {
                    if (value.equals("1") || value.equalsIgnoreCase("true")) {
                        sql.append(" and outstanding > 0");
                    }
                }
                case "collect" -> {
                    switch (value.toLowerCase(Locale.ROOT)) {
                        case "unpaid" -> sql.append(" and collect_status = 'UNPAID'");
                        case "partial" -> sql.append(" and collect_status = 'PARTIAL'");
                        case "paid" -> sql.append(" and collect_status = 'PAID'");
                        default -> {
                            // "any" restricts nothing.
                        }
                    }
                }
                case "notify" -> {
                    switch (value.toLowerCase(Locale.ROOT)) {
                        case "none" -> sql.append(" and notify_status = 'NONE'");
                        case "queued" -> sql.append(" and notify_status = 'QUEUED'");
                        case "sent" -> sql.append(" and notify_status = 'SENT'");
                        case "failed" -> sql.append(" and notify_status in ('FAILED', 'MISSING_EMAIL')");
                        default -> {
                            // "any" restricts nothing.
                        }
                    }
                }
                default -> {
                    // Unknown fields narrow nothing, like everywhere else.
                }
            }
        }
        params.search().ifPresent(term -> {
            sql.append(" and (lower(full_name) like :q or lower(student_code) like :q)");
            args.addValue("q", ListParams.likePattern(term));
        });
        return new Where(sql.toString(), args);
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    // ---- shared with the dispatcher

    /** What is frozen about one student when a notification is made. */
    public record Snapshot(long batchStudentId, String studentName, String studentCode, String classGroupName,
                           String className, String guardianName, String email, List<FeeLine> lines,
                           BigDecimal total) {
    }

    /** The bank account a payment request was made out to. */
    public record PaymentSnapshot(String number, String bank, String holder, String baseUrl, String template,
                                  String showInfo) {
    }

    /** Used by tests and the dispatcher to read a timestamp column back as an {@link Instant}. */
    public static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
