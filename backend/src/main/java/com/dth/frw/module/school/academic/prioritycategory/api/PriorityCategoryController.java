package com.dth.frw.module.school.academic.prioritycategory.api;

import com.dth.frw.domain.school.academic.PriorityCategory;
import com.dth.frw.module.school.academic.prioritycategory.api.dto.PriorityCategoryPatchRequest;
import com.dth.frw.module.school.academic.prioritycategory.api.dto.PriorityCategoryRequest;
import com.dth.frw.module.school.academic.prioritycategory.api.dto.PriorityCategoryResponse;
import com.dth.frw.module.school.academic.prioritycategory.service.PriorityCategoryService;
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
 * {@code /api/priority_categories} — the {@code priority_categories} resource. The path segment is the
 * resource name verbatim, and the first half of the permission codes below.
 */
@RestController
@RequestMapping("/api/priority_categories")
public class PriorityCategoryController {

    private final PriorityCategoryService service;
    private final RowLevelSecurity rowLevel;

    public PriorityCategoryController(PriorityCategoryService service, RowLevelSecurity rowLevel) {
        this.service = service;
        this.rowLevel = rowLevel;
    }

    /** Serves both {@code getList} and {@code getMany}; the latter is answered unpaged. */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.PRIORITY_CATEGORIES_READ + "')")
    public ResponseEntity<List<PriorityCategoryResponse>> list(@RequestParam MultiValueMap<String, String> query) {
        ListParams params = new ListParams(query);

        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            List<PriorityCategoryResponse> rows = service.getMany(ids).stream()
                    .map(row -> PriorityCategoryResponse.from(row, rowLevel))
                    .toList();
            return ListResponse.of(rows, rows.size());
        }

        Page<PriorityCategory> page = service.list(params);
        return ListResponse.of(
                page.map(row -> PriorityCategoryResponse.from(row, rowLevel)).getContent(),
                page.getTotalElements());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.PRIORITY_CATEGORIES_READ + "')")
    public PriorityCategoryResponse get(@PathVariable Long id) {
        return PriorityCategoryResponse.from(service.get(id), rowLevel);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.PRIORITY_CATEGORIES_WRITE + "')")
    public ResponseEntity<PriorityCategoryResponse> create(@Valid @RequestBody PriorityCategoryRequest request) {
        PriorityCategory created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/priority_categories/" + created.getId()))
                .body(PriorityCategoryResponse.from(created, rowLevel));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('" + Permissions.PRIORITY_CATEGORIES_WRITE + "')")
    public PriorityCategoryResponse update(
            @PathVariable Long id, @Valid @RequestBody PriorityCategoryPatchRequest request) {
        return PriorityCategoryResponse.from(service.update(id, request), rowLevel);
    }

    /** Echoes the deleted row — the data provider parses the response body. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.PRIORITY_CATEGORIES_DELETE + "')")
    public PriorityCategoryResponse delete(@PathVariable Long id) {
        return PriorityCategoryResponse.from(service.delete(id));
    }
}
