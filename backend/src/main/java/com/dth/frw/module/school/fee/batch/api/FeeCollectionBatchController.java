package com.dth.frw.module.school.fee.batch.api;

import com.dth.frw.domain.school.fee.FeeCollectionBatch;
import com.dth.frw.module.school.fee.batch.api.dto.FeeCollectionBatchItemResponse;
import com.dth.frw.module.school.fee.batch.api.dto.FeeCollectionBatchItemsRequest;
import com.dth.frw.module.school.fee.batch.api.dto.FeeCollectionBatchPreviewRequest;
import com.dth.frw.module.school.fee.batch.api.dto.FeeCollectionBatchPreviewResponse;
import com.dth.frw.module.school.fee.batch.api.dto.FeeCollectionBatchRequest;
import com.dth.frw.module.school.fee.batch.api.dto.FeeCollectionBatchRequest.ItemLine;
import com.dth.frw.module.school.fee.batch.api.dto.FeeCollectionBatchResponse;
import com.dth.frw.module.school.fee.batch.api.dto.FeeCollectionBatchStudentResponse;
import com.dth.frw.module.school.fee.batch.api.dto.FeeCollectionBatchStudentsResponse;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchService;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.Line;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.StudentRows;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.SyncResult;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.dth.frw.shared.security.Permissions;
import com.dth.frw.shared.web.ListParams;
import com.dth.frw.shared.web.ListResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
 * {@code /api/fee_collection_batches} — the {@code fee_collection_batches} resource. The path
 * segment is the resource name verbatim, and the first half of the permission codes below.
 *
 * <p>Beyond the data provider's dialect it has what a đợt thu needs: {@code POST /preview}
 * (what a đợt would be), {@code PUT /{id}/items} (change its khoản thu), {@code POST
 * /{id}/sync} (follow the registrations), and the two reads under it, {@code /items} and
 * {@code /students}. There is no patch: a đợt's name, month and scope are fixed once it
 * is made.
 */
@RestController
@RequestMapping("/api/fee_collection_batches")
public class FeeCollectionBatchController {

    private final FeeCollectionBatchService service;
    private final RowLevelSecurity rowLevel;

    public FeeCollectionBatchController(FeeCollectionBatchService service, RowLevelSecurity rowLevel) {
        this.service = service;
        this.rowLevel = rowLevel;
    }

    /** Serves both {@code getList} and {@code getMany}; the latter is answered unpaged. */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.FEE_COLLECTION_BATCHES_READ + "')")
    public ResponseEntity<List<FeeCollectionBatchResponse>> list(@RequestParam MultiValueMap<String, String> query) {
        ListParams params = new ListParams(query);
        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            List<FeeCollectionBatchResponse> rows = service.getMany(ids).stream()
                    .map(row -> FeeCollectionBatchResponse.from(row, rowLevel))
                    .toList();
            return ListResponse.of(rows, rows.size());
        }
        Page<FeeCollectionBatch> page = service.list(params);
        return ListResponse.of(
                page.map(row -> FeeCollectionBatchResponse.from(row, rowLevel)).getContent(),
                page.getTotalElements());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_COLLECTION_BATCHES_READ + "')")
    public FeeCollectionBatchResponse get(@PathVariable Long id) {
        return FeeCollectionBatchResponse.from(service.get(id), rowLevel);
    }

    /** Declared before {@code /{id}} routes that share its verb so the literal path wins. */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_COLLECTION_BATCHES_WRITE + "')")
    public FeeCollectionBatchPreviewResponse preview(@Valid @RequestBody FeeCollectionBatchPreviewRequest request) {
        return FeeCollectionBatchPreviewResponse.from(service.preview(
                request.academicYear().id(),
                request.collectionMonth(),
                request.scope(),
                request.scopeClassGroup() == null ? null : request.scopeClassGroup().id(),
                request.scopeClass() == null ? null : request.scopeClass().id(),
                lines(request.items())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.FEE_COLLECTION_BATCHES_WRITE + "')")
    public ResponseEntity<FeeCollectionBatchResponse> create(@Valid @RequestBody FeeCollectionBatchRequest request) {
        FeeCollectionBatch created = service.create(
                request.academicYear().id(),
                request.name(),
                request.collectionMonth(),
                request.scope(),
                request.scopeClassGroup() == null ? null : request.scopeClassGroup().id(),
                request.scopeClass() == null ? null : request.scopeClass().id(),
                lines(request.items()));
        return ResponseEntity
                .created(URI.create("/api/fee_collection_batches/" + created.getId()))
                .body(FeeCollectionBatchResponse.from(created, rowLevel));
    }

    @PutMapping("/{id}/items")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_COLLECTION_BATCHES_WRITE + "')")
    public FeeCollectionBatchResponse updateItems(@PathVariable Long id,
                                                  @Valid @RequestBody FeeCollectionBatchItemsRequest request) {
        return FeeCollectionBatchResponse.from(service.updateItems(id, lines(request.items())), rowLevel);
    }

    @PostMapping("/{id}/sync")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_COLLECTION_BATCHES_WRITE + "')")
    public SyncResult sync(@PathVariable Long id) {
        return service.syncRegistrations(id);
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_COLLECTION_BATCHES_READ + "')")
    public List<FeeCollectionBatchItemResponse> items(@PathVariable Long id) {
        return service.items(id).stream().map(FeeCollectionBatchItemResponse::from).toList();
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_COLLECTION_BATCHES_READ + "')")
    public FeeCollectionBatchStudentsResponse students(
            @PathVariable Long id, @RequestParam MultiValueMap<String, String> query) {
        StudentRows rows = service.students(id, new ListParams(query));
        return new FeeCollectionBatchStudentsResponse(
                rows.page().getContent().stream()
                        .map(row -> FeeCollectionBatchStudentResponse.from(row, rows.amountsByStudent().get(row.getId())))
                        .toList(),
                rows.page().getTotalElements());
    }

    /** Echoes the deleted row — the data provider parses the response body. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_COLLECTION_BATCHES_DELETE + "')")
    public FeeCollectionBatchResponse delete(@PathVariable Long id) {
        return FeeCollectionBatchResponse.from(service.delete(id));
    }

    private static List<Line> lines(List<ItemLine> items) {
        return items == null
                ? List.of()
                : items.stream().map(line -> new Line(line.feeItemId(), line.quantity())).toList();
    }
}
