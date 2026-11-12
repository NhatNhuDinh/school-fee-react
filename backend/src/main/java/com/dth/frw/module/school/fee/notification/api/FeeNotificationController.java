package com.dth.frw.module.school.fee.notification.api;

import com.dth.frw.domain.school.fee.FeeNotification;
import com.dth.frw.module.school.fee.notification.api.dto.FeeNotificationActionRequests;
import com.dth.frw.module.school.fee.notification.api.dto.FeeNotificationRequest;
import com.dth.frw.module.school.fee.notification.api.dto.FeeNotificationResponse;
import com.dth.frw.module.school.fee.notification.api.dto.FeeNotificationUpdateRequest;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationService;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.CandidatePage;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.Created;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.IdAmount;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.Mode;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.RecipientPage;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.Rendered;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.Requeued;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.Tree;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.dth.frw.shared.security.Permissions;
import com.dth.frw.shared.web.ListParams;
import com.dth.frw.shared.web.ListResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/fee_notifications} — the {@code fee_notifications} resource. The path segment is
 * the resource name verbatim, and the first half of the permission codes below.
 *
 * <p>Beyond the data provider's dialect it has what choosing and sending needs, all declared
 * before {@code /{id}} so the literal paths win: {@code GET /defaults} (what a new notification
 * starts from), {@code /candidates}, {@code /candidate-ids} and {@code /tree} (who it could go
 * to), {@code POST /preview} (the e-mail one student would get), and under a notification
 * {@code /recipients}, {@code POST /send}, {@code POST /retry}. There is no create-then-patch:
 * who it goes to is fixed once it is made, and a draft can only have its words changed.
 */
@RestController
@RequestMapping("/api/fee_notifications")
public class FeeNotificationController {

    private final FeeNotificationService service;
    private final RowLevelSecurity rowLevel;

    public FeeNotificationController(FeeNotificationService service, RowLevelSecurity rowLevel) {
        this.service = service;
        this.rowLevel = rowLevel;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_READ + "')")
    public ResponseEntity<List<FeeNotificationResponse>> list(@RequestParam MultiValueMap<String, String> query) {
        ListParams params = new ListParams(query);
        Page<FeeNotification> page = service.list(params);
        return ListResponse.of(
                page.map(row -> FeeNotificationResponse.from(row, rowLevel)).getContent(),
                page.getTotalElements());
    }

    @GetMapping("/defaults")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_WRITE + "')")
    public Map<String, Object> defaults(@RequestParam Long batchId) {
        return service.defaults(batchId);
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_WRITE + "')")
    public CandidatePage candidates(@RequestParam Long batchId,
                                    @RequestParam(defaultValue = "ALL") String mode,
                                    @RequestParam MultiValueMap<String, String> query) {
        return service.candidates(batchId, new ListParams(query), mode(mode));
    }

    @GetMapping("/candidate-ids")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_WRITE + "')")
    public List<IdAmount> candidateIds(@RequestParam Long batchId,
                                       @RequestParam(defaultValue = "ALL") String mode,
                                       @RequestParam MultiValueMap<String, String> query) {
        return service.candidateIds(batchId, new ListParams(query), mode(mode));
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_WRITE + "')")
    public Tree tree(@RequestParam Long batchId,
                     @RequestParam(defaultValue = "ALL") String mode,
                     @RequestParam MultiValueMap<String, String> query) {
        return service.tree(batchId, new ListParams(query), mode(mode));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_WRITE + "')")
    public Rendered preview(@Valid @RequestBody FeeNotificationActionRequests.Preview request) {
        return service.preview(request.batchId(), request.batchStudentId(), request.subject(), request.content(),
                request.deadline());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_READ + "')")
    public FeeNotificationResponse get(@PathVariable Long id) {
        return FeeNotificationResponse.from(service.get(id), rowLevel);
    }

    @GetMapping("/{id}/recipients")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_READ + "')")
    public RecipientPage recipients(@PathVariable Long id, @RequestParam MultiValueMap<String, String> query) {
        return service.recipients(id, new ListParams(query));
    }

    /** The notification, and how many of those picked were left out and why. */
    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_WRITE + "')")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody FeeNotificationRequest request) {
        Created created = service.create(request.batch().id(), request.batchStudentIds(), request.subject(),
                request.content(), request.deadline(), request.sendAt(), request.draft());
        Map<String, Object> body = Map.of(
                "notification", FeeNotificationResponse.from(created.notification(), rowLevel),
                "requested", created.requested(),
                "skippedAlreadyNotified", created.skippedAlreadyNotified(),
                "skippedNoDebt", created.skippedNoDebt());
        return ResponseEntity.created(URI.create("/api/fee_notifications/" + created.notification().getId())).body(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_WRITE + "')")
    public FeeNotificationResponse update(@PathVariable Long id, @Valid @RequestBody FeeNotificationUpdateRequest request) {
        return FeeNotificationResponse.from(
                service.updateDraft(id, request.subject(), request.content(), request.deadline()), rowLevel);
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_WRITE + "')")
    public FeeNotificationResponse send(@PathVariable Long id,
                                        @RequestBody(required = false) FeeNotificationActionRequests.Send request) {
        return FeeNotificationResponse.from(service.send(id, request == null ? null : request.sendAt()), rowLevel);
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_WRITE + "')")
    public Requeued retry(@PathVariable Long id,
                          @RequestBody(required = false) FeeNotificationActionRequests.Retry request) {
        return service.retry(id, request == null ? null : request.recipientIds());
    }

    /** Cancels a draft or a scheduled notification; echoes it, because the data provider parses a body. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_NOTIFICATIONS_DELETE + "')")
    public FeeNotificationResponse delete(@PathVariable Long id) {
        return FeeNotificationResponse.from(service.cancel(id));
    }

    private static Mode mode(String value) {
        try {
            return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Mode.ALL;
        }
    }
}
