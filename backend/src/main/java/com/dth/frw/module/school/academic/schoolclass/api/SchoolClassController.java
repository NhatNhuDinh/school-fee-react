package com.dth.frw.module.school.academic.schoolclass.api;

import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.module.school.academic.schoolclass.api.dto.SchoolClassPatchRequest;
import com.dth.frw.module.school.academic.schoolclass.api.dto.SchoolClassRequest;
import com.dth.frw.module.school.academic.schoolclass.api.dto.SchoolClassResponse;
import com.dth.frw.module.school.academic.schoolclass.service.SchoolClassService;
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
 * {@code /api/school_classes} — the {@code school_classes} resource. The path segment is the
 * resource name verbatim, and the first half of the permission codes below.
 */
@RestController
@RequestMapping("/api/school_classes")
public class SchoolClassController {

    private final SchoolClassService service;
    private final RowLevelSecurity rowLevel;

    public SchoolClassController(SchoolClassService service, RowLevelSecurity rowLevel) {
        this.service = service;
        this.rowLevel = rowLevel;
    }

    /** Serves both {@code getList} and {@code getMany}; the latter is answered unpaged. */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.SCHOOL_CLASSES_READ + "')")
    public ResponseEntity<List<SchoolClassResponse>> list(@RequestParam MultiValueMap<String, String> query) {
        ListParams params = new ListParams(query);

        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            List<SchoolClassResponse> rows = service.getMany(ids).stream()
                    .map(row -> SchoolClassResponse.from(row, rowLevel))
                    .toList();
            return ListResponse.of(rows, rows.size());
        }

        Page<SchoolClass> page = service.list(params);
        return ListResponse.of(
                page.map(row -> SchoolClassResponse.from(row, rowLevel)).getContent(),
                page.getTotalElements());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.SCHOOL_CLASSES_READ + "')")
    public SchoolClassResponse get(@PathVariable Long id) {
        return SchoolClassResponse.from(service.get(id), rowLevel);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.SCHOOL_CLASSES_WRITE + "')")
    public ResponseEntity<SchoolClassResponse> create(@Valid @RequestBody SchoolClassRequest request) {
        SchoolClass created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/school_classes/" + created.getId()))
                .body(SchoolClassResponse.from(created, rowLevel));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('" + Permissions.SCHOOL_CLASSES_WRITE + "')")
    public SchoolClassResponse update(
            @PathVariable Long id, @Valid @RequestBody SchoolClassPatchRequest request) {
        return SchoolClassResponse.from(service.update(id, request), rowLevel);
    }

    /** Echoes the deleted row — the data provider parses the response body. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.SCHOOL_CLASSES_DELETE + "')")
    public SchoolClassResponse delete(@PathVariable Long id) {
        return SchoolClassResponse.from(service.delete(id));
    }
}
