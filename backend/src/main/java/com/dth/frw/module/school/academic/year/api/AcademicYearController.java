package com.dth.frw.module.school.academic.year.api;

import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.module.school.academic.year.api.dto.AcademicYearPatchRequest;
import com.dth.frw.module.school.academic.year.api.dto.AcademicYearRequest;
import com.dth.frw.module.school.academic.year.api.dto.AcademicYearResponse;
import com.dth.frw.module.school.academic.year.service.AcademicYearService;
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
 * {@code /api/academic_years} — the {@code academic_years} resource.
 *
 * <p>The path segment is the resource name verbatim, as the frontend's data
 * provider builds {@code {apiURL}/{resource}}; the same name is the first half
 * of the permission codes checked below.
 */
@RestController
@RequestMapping("/api/academic_years")
public class AcademicYearController {

    private final AcademicYearService service;
    private final RowLevelSecurity rowLevel;

    public AcademicYearController(AcademicYearService service, RowLevelSecurity rowLevel) {
        this.service = service;
        this.rowLevel = rowLevel;
    }

    /** Serves both {@code getList} and {@code getMany}; the latter is answered unpaged. */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ACADEMIC_YEARS_READ + "')")
    public ResponseEntity<List<AcademicYearResponse>> list(@RequestParam MultiValueMap<String, String> query) {
        ListParams params = new ListParams(query);

        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            List<AcademicYearResponse> rows = service.getMany(ids).stream()
                    .map(row -> AcademicYearResponse.from(row, rowLevel))
                    .toList();
            return ListResponse.of(rows, rows.size());
        }

        Page<AcademicYear> page = service.list(params);
        return ListResponse.of(
                page.map(row -> AcademicYearResponse.from(row, rowLevel)).getContent(),
                page.getTotalElements());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ACADEMIC_YEARS_READ + "')")
    public AcademicYearResponse get(@PathVariable Long id) {
        return AcademicYearResponse.from(service.get(id), rowLevel);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.ACADEMIC_YEARS_WRITE + "')")
    public ResponseEntity<AcademicYearResponse> create(@Valid @RequestBody AcademicYearRequest request) {
        AcademicYear created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/academic_years/" + created.getId()))
                .body(AcademicYearResponse.from(created, rowLevel));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('" + Permissions.ACADEMIC_YEARS_WRITE + "')")
    public AcademicYearResponse update(
            @PathVariable Long id, @Valid @RequestBody AcademicYearPatchRequest request) {
        return AcademicYearResponse.from(service.update(id, request), rowLevel);
    }

    /** Echoes the deleted row — the data provider parses the response body. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ACADEMIC_YEARS_DELETE + "')")
    public AcademicYearResponse delete(@PathVariable Long id) {
        return AcademicYearResponse.from(service.delete(id));
    }
}
