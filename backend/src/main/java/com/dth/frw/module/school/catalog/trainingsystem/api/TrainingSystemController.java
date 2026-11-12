package com.dth.frw.module.school.catalog.trainingsystem.api;

import com.dth.frw.domain.school.catalog.TrainingSystem;
import com.dth.frw.module.school.catalog.trainingsystem.api.dto.TrainingSystemPatchRequest;
import com.dth.frw.module.school.catalog.trainingsystem.api.dto.TrainingSystemRequest;
import com.dth.frw.module.school.catalog.trainingsystem.api.dto.TrainingSystemResponse;
import com.dth.frw.module.school.catalog.trainingsystem.service.TrainingSystemService;
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
 * {@code /api/training_systems} — the {@code training_systems} resource. The path segment is the
 * resource name verbatim, and the first half of the permission codes below.
 */
@RestController
@RequestMapping("/api/training_systems")
public class TrainingSystemController {

    private final TrainingSystemService service;
    private final RowLevelSecurity rowLevel;

    public TrainingSystemController(TrainingSystemService service, RowLevelSecurity rowLevel) {
        this.service = service;
        this.rowLevel = rowLevel;
    }

    /** Serves both {@code getList} and {@code getMany}; the latter is answered unpaged. */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.TRAINING_SYSTEMS_READ + "')")
    public ResponseEntity<List<TrainingSystemResponse>> list(@RequestParam MultiValueMap<String, String> query) {
        ListParams params = new ListParams(query);

        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            List<TrainingSystemResponse> rows = service.getMany(ids).stream()
                    .map(row -> TrainingSystemResponse.from(row, rowLevel))
                    .toList();
            return ListResponse.of(rows, rows.size());
        }

        Page<TrainingSystem> page = service.list(params);
        return ListResponse.of(
                page.map(row -> TrainingSystemResponse.from(row, rowLevel)).getContent(),
                page.getTotalElements());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.TRAINING_SYSTEMS_READ + "')")
    public TrainingSystemResponse get(@PathVariable Long id) {
        return TrainingSystemResponse.from(service.get(id), rowLevel);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.TRAINING_SYSTEMS_WRITE + "')")
    public ResponseEntity<TrainingSystemResponse> create(@Valid @RequestBody TrainingSystemRequest request) {
        TrainingSystem created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/training_systems/" + created.getId()))
                .body(TrainingSystemResponse.from(created, rowLevel));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('" + Permissions.TRAINING_SYSTEMS_WRITE + "')")
    public TrainingSystemResponse update(
            @PathVariable Long id, @Valid @RequestBody TrainingSystemPatchRequest request) {
        return TrainingSystemResponse.from(service.update(id, request), rowLevel);
    }

    /** Echoes the deleted row — the data provider parses the response body. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.TRAINING_SYSTEMS_DELETE + "')")
    public TrainingSystemResponse delete(@PathVariable Long id) {
        return TrainingSystemResponse.from(service.delete(id));
    }
}
