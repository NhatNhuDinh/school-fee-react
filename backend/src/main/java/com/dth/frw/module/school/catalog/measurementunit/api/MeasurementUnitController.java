package com.dth.frw.module.school.catalog.measurementunit.api;

import com.dth.frw.domain.school.catalog.MeasurementUnit;
import com.dth.frw.module.school.catalog.measurementunit.api.dto.MeasurementUnitPatchRequest;
import com.dth.frw.module.school.catalog.measurementunit.api.dto.MeasurementUnitRequest;
import com.dth.frw.module.school.catalog.measurementunit.api.dto.MeasurementUnitResponse;
import com.dth.frw.module.school.catalog.measurementunit.service.MeasurementUnitService;
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
 * {@code /api/measurement_units} — the {@code measurement_units} resource. The path segment is the
 * resource name verbatim, and the first half of the permission codes below.
 */
@RestController
@RequestMapping("/api/measurement_units")
public class MeasurementUnitController {

    private final MeasurementUnitService service;
    private final RowLevelSecurity rowLevel;

    public MeasurementUnitController(MeasurementUnitService service, RowLevelSecurity rowLevel) {
        this.service = service;
        this.rowLevel = rowLevel;
    }

    /** Serves both {@code getList} and {@code getMany}; the latter is answered unpaged. */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.MEASUREMENT_UNITS_READ + "')")
    public ResponseEntity<List<MeasurementUnitResponse>> list(@RequestParam MultiValueMap<String, String> query) {
        ListParams params = new ListParams(query);

        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            List<MeasurementUnitResponse> rows = service.getMany(ids).stream()
                    .map(row -> MeasurementUnitResponse.from(row, rowLevel))
                    .toList();
            return ListResponse.of(rows, rows.size());
        }

        Page<MeasurementUnit> page = service.list(params);
        return ListResponse.of(
                page.map(row -> MeasurementUnitResponse.from(row, rowLevel)).getContent(),
                page.getTotalElements());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.MEASUREMENT_UNITS_READ + "')")
    public MeasurementUnitResponse get(@PathVariable Long id) {
        return MeasurementUnitResponse.from(service.get(id), rowLevel);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.MEASUREMENT_UNITS_WRITE + "')")
    public ResponseEntity<MeasurementUnitResponse> create(@Valid @RequestBody MeasurementUnitRequest request) {
        MeasurementUnit created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/measurement_units/" + created.getId()))
                .body(MeasurementUnitResponse.from(created, rowLevel));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('" + Permissions.MEASUREMENT_UNITS_WRITE + "')")
    public MeasurementUnitResponse update(
            @PathVariable Long id, @Valid @RequestBody MeasurementUnitPatchRequest request) {
        return MeasurementUnitResponse.from(service.update(id, request), rowLevel);
    }

    /** Echoes the deleted row — the data provider parses the response body. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.MEASUREMENT_UNITS_DELETE + "')")
    public MeasurementUnitResponse delete(@PathVariable Long id) {
        return MeasurementUnitResponse.from(service.delete(id));
    }
}
