package com.dth.frw.module.school.fee.registration.service;

import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.fee.FeeItem;
import com.dth.frw.domain.school.fee.FeeRegistrationSource;
import com.dth.frw.domain.school.fee.StudentFeeRegistration;
import com.dth.frw.domain.school.student.StudentEnrollment;
import com.dth.frw.module.school.academic.schoolclass.service.SchoolClassService;
import com.dth.frw.module.school.fee.item.service.FeeItemService;
import com.dth.frw.module.school.fee.registration.api.dto.FeeRegistrationMatrixResponse;
import com.dth.frw.module.school.fee.registration.api.dto.FeeRegistrationSaveRequest;
import com.dth.frw.module.school.fee.registration.repo.FeeRegistrationRepository;
import com.dth.frw.module.school.student.service.StudentService;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import com.dth.frw.shared.exception.ValidationFailedException;
import com.dth.frw.shared.security.rowlevel.Action;
import com.dth.frw.shared.web.ListParams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;

/**
 * Registering students for optional khoản thu, one lớp at a time.
 *
 * <p>A registration is a row that exists exactly when the student is registered, so a
 * save is a comparison, not a list of commands: the screen sends the whole state of the
 * matrix, and what is new is inserted and what is gone is deleted. That makes a save
 * idempotent, and means two people saving the same lớp cannot double-register anyone —
 * the unique constraint is the last line, and the comparison is the first.
 *
 * <p>Only the cells of the matrix are compared. A registration for a khoản thu that is
 * no longer a column (stopped, or made mandatory after the student registered) is not
 * visible and is not touched: unticking is the only way to unregister, and a person
 * cannot untick what they cannot see.
 */
@Service
@Transactional(readOnly = true)
public class FeeRegistrationService {

    private static final int CLASS_SIZE_LIMIT = 1000;

    private final FeeRegistrationRepository repository;
    private final SchoolClassService schoolClassService;
    private final StudentService studentService;
    private final FeeItemService feeItemService;
    private final RowLevelSecurity rowLevel;

    public FeeRegistrationService(FeeRegistrationRepository repository,
                                  SchoolClassService schoolClassService,
                                  StudentService studentService,
                                  FeeItemService feeItemService,
                                  RowLevelSecurity rowLevel) {
        this.repository = repository;
        this.schoolClassService = schoolClassService;
        this.studentService = studentService;
        this.feeItemService = feeItemService;
        this.rowLevel = rowLevel;
    }

    /** What the screen draws for a lớp. A lớp the caller cannot see is a 404. */
    public FeeRegistrationMatrixResponse matrix(Long schoolClassId) {
        SchoolClass schoolClass = schoolClassService.get(schoolClassId);
        List<FeeItem> columns = feeItemService.optionalFor(schoolClass);
        List<StudentEnrollment> students = studentsOf(schoolClass);
        List<StudentFeeRegistration> registered = registrationsOf(students, columns);
        return FeeRegistrationMatrixResponse.of(
                schoolClass, schoolClass.getClassGroup().getAcademicYear(), columns, students, registered);
    }

    /**
     * Who is registered for each of these khoản thu, as enrollment ids by khoản thu.
     * Read through the caller's scope, so a registration they may not see is not one.
     */
    public Map<Long, Set<Long>> registeredEnrollments(Collection<Long> feeItemIds) {
        if (feeItemIds.isEmpty()) {
            return Map.of();
        }
        Specification<StudentFeeRegistration> ofItems =
                (root, query, cb) -> root.get("feeItem").get("id").in(feeItemIds);
        Map<Long, Set<Long>> result = new HashMap<>();
        for (StudentFeeRegistration row : repository.findAll(rowLevel.scope(StudentFeeRegistration.class, Action.READ).and(ofItems))) {
            result.computeIfAbsent(row.getFeeItem().getId(), id -> new HashSet<>()).add(row.getEnrollment().getId());
        }
        return result;
    }

    /** Brings the lớp's registrations to the state sent, and says how many rows that took. */
    @Transactional
    public Result save(Long schoolClassId, FeeRegistrationSaveRequest request) {
        SchoolClass schoolClass = schoolClassService.get(schoolClassId);
        List<FeeItem> columns = feeItemService.optionalFor(schoolClass);
        List<StudentEnrollment> students = studentsOf(schoolClass);

        Map<Long, StudentEnrollment> studentsById =
                students.stream().collect(Collectors.toMap(StudentEnrollment::getId, Function.identity()));
        Map<Long, FeeItem> columnsById =
                columns.stream().collect(Collectors.toMap(FeeItem::getId, Function.identity()));

        // The state asked for, refused outright if it names something that is not on the
        // screen: a student who has since moved class, or a khoản thu stopped since it
        // was loaded. Silently dropping it would report a save that did not happen.
        Set<Key> wanted = new LinkedHashSet<>();
        for (FeeRegistrationSaveRequest.Row row : request.registrations()) {
            if (!studentsById.containsKey(row.enrollmentId())) {
                throw invalid("Học sinh không còn thuộc lớp này. Hãy tải lại trang rồi đăng ký lại.");
            }
            for (Long feeItemId : row.feeItemIds()) {
                if (!columnsById.containsKey(feeItemId)) {
                    throw invalid("Có khoản thu không còn áp dụng được cho lớp này. Hãy tải lại trang.");
                }
                wanted.add(new Key(row.enrollmentId(), feeItemId));
            }
        }

        List<StudentFeeRegistration> existing = registrationsOf(students, columns);
        Set<Key> present = existing.stream()
                .map(r -> new Key(r.getEnrollment().getId(), r.getFeeItem().getId()))
                .collect(Collectors.toSet());

        int added = 0;
        for (Key key : wanted) {
            if (present.contains(key)) {
                continue;
            }
            StudentFeeRegistration row = new StudentFeeRegistration(
                    studentsById.get(key.enrollmentId()), columnsById.get(key.feeItemId()), FeeRegistrationSource.SCHOOL);
            repository.persist(row);
            // Flushed before the policy is asked, as everywhere: the identity key and
            // the defaults only exist afterwards.
            repository.flush();
            rowLevel.checkWritable(row);
            added++;
        }

        List<Long> toDelete = existing.stream()
                .filter(r -> !wanted.contains(new Key(r.getEnrollment().getId(), r.getFeeItem().getId())))
                .map(StudentFeeRegistration::getId)
                .toList();
        if (!toDelete.isEmpty()) {
            // Read back through the delete scope: being allowed to see a registration is
            // not being allowed to remove it.
            List<StudentFeeRegistration> removable =
                    repository.findAll(rowLevel.byIds(StudentFeeRegistration.class, Action.DELETE, toDelete));
            if (removable.size() != toDelete.size()) {
                throw new ResourceNotFoundException("đăng ký thu", toDelete.get(0));
            }
            removable.forEach(repository::remove);
        }
        return new Result(added, toDelete.size());
    }

    /** How many registrations a save inserted and deleted. */
    public record Result(int added, int removed) {
    }

    // ---- helpers

    private record Key(Long enrollmentId, Long feeItemId) {
    }

    /** The students of the lớp, in the order the student list shows them. */
    private List<StudentEnrollment> studentsOf(SchoolClass schoolClass) {
        LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("academicYear.id", String.valueOf(schoolClass.getClassGroup().getAcademicYear().getId()));
        query.add("schoolClass.id", String.valueOf(schoolClass.getId()));
        query.add("_start", "0");
        query.add("_end", String.valueOf(CLASS_SIZE_LIMIT));
        return studentService.list(new ListParams(query)).getContent();
    }

    /** The registrations of these students for these columns — and no others. */
    private List<StudentFeeRegistration> registrationsOf(List<StudentEnrollment> students, List<FeeItem> columns) {
        if (students.isEmpty() || columns.isEmpty()) {
            return List.of();
        }
        Set<Long> enrollmentIds = students.stream().map(StudentEnrollment::getId).collect(Collectors.toSet());
        Set<Long> columnIds = new HashSet<>(columns.stream().map(FeeItem::getId).toList());
        List<StudentFeeRegistration> rows = repository.findAll(
                rowLevel.scope(StudentFeeRegistration.class, Action.READ)
                        .and(FeeRegistrationRepository.ofEnrollments(enrollmentIds)));
        List<StudentFeeRegistration> visible = new ArrayList<>();
        for (StudentFeeRegistration row : rows) {
            if (columnIds.contains(row.getFeeItem().getId())) {
                visible.add(row);
            }
        }
        return visible;
    }

    private static ValidationFailedException invalid(String message) {
        return new ValidationFailedException("Dữ liệu không hợp lệ", Map.of("registrations", message));
    }
}
