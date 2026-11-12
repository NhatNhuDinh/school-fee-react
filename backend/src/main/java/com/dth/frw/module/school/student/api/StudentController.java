package com.dth.frw.module.school.student.api;

import com.dth.frw.domain.school.student.StudentEnrollment;
import com.dth.frw.module.school.student.api.dto.AssignClassRequest;
import com.dth.frw.module.school.student.api.dto.CountResponse;
import com.dth.frw.module.school.student.api.dto.ImportStudentsRequest;
import com.dth.frw.module.school.student.api.dto.StudentPatchRequest;
import com.dth.frw.module.school.student.api.dto.StudentRequest;
import com.dth.frw.module.school.student.api.dto.StudentResponse;
import com.dth.frw.module.school.student.api.dto.StudentStructureResponse;
import com.dth.frw.module.school.student.service.StudentService;
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
 * {@code /api/students} — the {@code students} resource: a student in a school
 * year. The path segment is the resource name verbatim, and the first half of
 * the permission codes below. {@code structure}, {@code assign-class} and
 * {@code import} are declared before {@code /{id}} so the literal paths win.
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService service;
    private final RowLevelSecurity rowLevel;

    public StudentController(StudentService service, RowLevelSecurity rowLevel) {
        this.service = service;
        this.rowLevel = rowLevel;
    }

    /** Serves both {@code getList} and {@code getMany}; the latter is answered unpaged. */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.STUDENTS_READ + "')")
    public ResponseEntity<List<StudentResponse>> list(@RequestParam MultiValueMap<String, String> query) {
        ListParams params = new ListParams(query);

        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            List<StudentResponse> rows = service.getMany(ids).stream()
                    .map(row -> StudentResponse.from(row, rowLevel))
                    .toList();
            return ListResponse.of(rows, rows.size());
        }

        Page<StudentEnrollment> page = service.list(params);
        return ListResponse.of(
                page.map(row -> StudentResponse.from(row, rowLevel)).getContent(),
                page.getTotalElements());
    }

    /** The khối lớp and lớp of a year with their student counts. */
    @GetMapping("/structure")
    @PreAuthorize("hasAuthority('" + Permissions.STUDENTS_READ + "')")
    public StudentStructureResponse structure(@RequestParam("academicYearId") Long academicYearId) {
        return service.structure(academicYearId);
    }

    @PostMapping("/assign-class")
    @PreAuthorize("hasAuthority('" + Permissions.STUDENTS_WRITE + "')")
    public CountResponse assignClass(@Valid @RequestBody AssignClassRequest request) {
        return new CountResponse(service.assignClass(request));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('" + Permissions.STUDENTS_WRITE + "')")
    public CountResponse importFromYear(@Valid @RequestBody ImportStudentsRequest request) {
        return new CountResponse(service.importFromYear(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.STUDENTS_READ + "')")
    public StudentResponse get(@PathVariable Long id) {
        return StudentResponse.from(service.get(id), rowLevel);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.STUDENTS_WRITE + "')")
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest request) {
        StudentEnrollment created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/students/" + created.getId()))
                .body(StudentResponse.from(created, rowLevel));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('" + Permissions.STUDENTS_WRITE + "')")
    public StudentResponse update(@PathVariable Long id, @Valid @RequestBody StudentPatchRequest request) {
        return StudentResponse.from(service.update(id, request), rowLevel);
    }

    /** Echoes the deleted row — the data provider parses the response body. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.STUDENTS_DELETE + "')")
    public StudentResponse delete(@PathVariable Long id) {
        return StudentResponse.from(service.delete(id));
    }
}
