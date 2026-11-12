package com.dth.frw.module.school.academic.classgroup.api;

import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.module.school.academic.classgroup.api.dto.ClassGroupPatchRequest;
import com.dth.frw.module.school.academic.classgroup.api.dto.ClassGroupRequest;
import com.dth.frw.module.school.academic.classgroup.api.dto.ClassGroupResponse;
import com.dth.frw.module.school.academic.classgroup.service.ClassGroupService;
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
 * {@code /api/class_groups} — the {@code class_groups} resource. The path segment is the
 * resource name verbatim, and the first half of the permission codes below.
 */
@RestController
@RequestMapping("/api/class_groups")
public class ClassGroupController {

    private final ClassGroupService service;
    private final RowLevelSecurity rowLevel;

    public ClassGroupController(ClassGroupService service, RowLevelSecurity rowLevel) {
        this.service = service;
        this.rowLevel = rowLevel;
    }

    /** Serves both {@code getList} and {@code getMany}; the latter is answered unpaged. */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.CLASS_GROUPS_READ + "')")
    public ResponseEntity<List<ClassGroupResponse>> list(@RequestParam MultiValueMap<String, String> query) {
        ListParams params = new ListParams(query);

        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            List<ClassGroupResponse> rows = service.getMany(ids).stream()
                    .map(row -> ClassGroupResponse.from(row, rowLevel))
                    .toList();
            return ListResponse.of(rows, rows.size());
        }

        Page<ClassGroup> page = service.list(params);
        return ListResponse.of(
                page.map(row -> ClassGroupResponse.from(row, rowLevel)).getContent(),
                page.getTotalElements());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.CLASS_GROUPS_READ + "')")
    public ClassGroupResponse get(@PathVariable Long id) {
        return ClassGroupResponse.from(service.get(id), rowLevel);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.CLASS_GROUPS_WRITE + "')")
    public ResponseEntity<ClassGroupResponse> create(@Valid @RequestBody ClassGroupRequest request) {
        ClassGroup created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/class_groups/" + created.getId()))
                .body(ClassGroupResponse.from(created, rowLevel));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('" + Permissions.CLASS_GROUPS_WRITE + "')")
    public ClassGroupResponse update(
            @PathVariable Long id, @Valid @RequestBody ClassGroupPatchRequest request) {
        return ClassGroupResponse.from(service.update(id, request), rowLevel);
    }

    /** Echoes the deleted row — the data provider parses the response body. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.CLASS_GROUPS_DELETE + "')")
    public ClassGroupResponse delete(@PathVariable Long id) {
        return ClassGroupResponse.from(service.delete(id));
    }
}
