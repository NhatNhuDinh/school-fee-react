package com.dth.frw.module.school.fee.item.api;

import com.dth.frw.domain.school.fee.FeeItem;
import com.dth.frw.module.school.fee.item.api.dto.FeeItemPatchRequest;
import com.dth.frw.module.school.fee.item.api.dto.FeeItemRequest;
import com.dth.frw.module.school.fee.item.api.dto.FeeItemResponse;
import com.dth.frw.module.school.fee.item.service.FeeItemService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/fee_items} — the {@code fee_items} resource. The path segment is the
 * resource name verbatim, and the first half of the permission codes below.
 */
@RestController
@RequestMapping("/api/fee_items")
public class FeeItemController {

    private final FeeItemService service;
    private final RowLevelSecurity rowLevel;

    public FeeItemController(FeeItemService service, RowLevelSecurity rowLevel) {
        this.service = service;
        this.rowLevel = rowLevel;
    }

    /** Serves both {@code getList} and {@code getMany}; the latter is answered unpaged. */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.FEE_ITEMS_READ + "')")
    public ResponseEntity<List<FeeItemResponse>> list(@RequestParam MultiValueMap<String, String> query) {
        ListParams params = new ListParams(query);

        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            List<FeeItemResponse> rows = service.getMany(ids).stream()
                    .map(row -> FeeItemResponse.from(row, rowLevel))
                    .toList();
            return ListResponse.of(rows, rows.size());
        }

        Page<FeeItem> page = service.list(params);
        return ListResponse.of(
                page.map(row -> FeeItemResponse.from(row, rowLevel)).getContent(),
                page.getTotalElements());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_ITEMS_READ + "')")
    public FeeItemResponse get(@PathVariable Long id) {
        return FeeItemResponse.from(service.get(id), rowLevel);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.FEE_ITEMS_WRITE + "')")
    public ResponseEntity<FeeItemResponse> create(@Valid @RequestBody FeeItemRequest request) {
        FeeItem created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/fee_items/" + created.getId()))
                .body(FeeItemResponse.from(created, rowLevel));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('" + Permissions.FEE_ITEMS_WRITE + "')")
    public FeeItemResponse update(@PathVariable Long id, @Valid @RequestBody FeeItemPatchRequest request) {
        return FeeItemResponse.from(service.update(id, request), rowLevel);
    }

    /** Echoes the deleted row — the data provider parses the response body. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_ITEMS_DELETE + "')")
    public FeeItemResponse delete(@PathVariable Long id) {
        return FeeItemResponse.from(service.delete(id));
    }
}
