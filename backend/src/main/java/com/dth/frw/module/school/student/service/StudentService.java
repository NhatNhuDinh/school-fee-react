package com.dth.frw.module.school.student.service;

import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.catalog.TrainingSystem;
import com.dth.frw.domain.school.student.Student;
import com.dth.frw.domain.school.student.StudentEnrollment;
import com.dth.frw.module.school.academic.classgroup.service.ClassGroupService;
import com.dth.frw.module.school.academic.schoolclass.service.SchoolClassService;
import com.dth.frw.module.school.academic.year.service.AcademicYearService;
import com.dth.frw.module.school.catalog.trainingsystem.service.TrainingSystemService;
import com.dth.frw.module.school.student.api.dto.AssignClassRequest;
import com.dth.frw.module.school.student.api.dto.ImportStudentsRequest;
import com.dth.frw.module.school.student.api.dto.StudentPatchRequest;
import com.dth.frw.module.school.student.api.dto.StudentRequest;
import com.dth.frw.module.school.student.api.dto.StudentStructureResponse;
import com.dth.frw.module.school.student.repo.StudentEnrollmentRepository;
import com.dth.frw.module.school.student.repo.StudentEnrollmentSpecifications;
import com.dth.frw.module.school.student.repo.StudentRepository;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.dth.frw.shared.exception.ConflictException;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import com.dth.frw.shared.exception.ValidationFailedException;
import com.dth.frw.shared.security.rowlevel.Action;
import com.dth.frw.shared.web.ListParams;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;

/**
 * The students of a school year. The managed row is the enrollment — the student
 * in a year — and creating one creates the student with it, as the Jmix wizard
 * did; the list, the class tree, assigning a class and carrying students over
 * from the previous year all work on enrollments.
 *
 * <p>A student code is unique within the school, as is a personal id when there
 * is one. A student has at most one enrollment per year, and the class has to
 * belong to the year.
 */
@Service
@Transactional(readOnly = true)
public class StudentService {

    /** Sort properties the list endpoint accepts; anything else is ignored. */
    private static final Set<String> SORTABLE = Set.of(
            "id", "createdAt", "transferInDate",
            "student.studentCode", "student.fullName", "student.dateOfBirth", "student.gender",
            "student.guardianName", "student.guardianPhone", "schoolClass.name");

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.asc("student.fullName"), Sort.Order.asc("student.studentCode"), Sort.Order.asc("id"));

    private static final String RESOURCE = "học sinh";

    private final StudentEnrollmentRepository enrollments;
    private final StudentRepository students;
    private final AcademicYearService academicYearService;
    private final ClassGroupService classGroupService;
    private final SchoolClassService schoolClassService;
    private final TrainingSystemService trainingSystemService;
    private final RowLevelSecurity rowLevel;

    public StudentService(StudentEnrollmentRepository enrollments,
                          StudentRepository students,
                          AcademicYearService academicYearService,
                          ClassGroupService classGroupService,
                          SchoolClassService schoolClassService,
                          TrainingSystemService trainingSystemService,
                          RowLevelSecurity rowLevel) {
        this.enrollments = enrollments;
        this.students = students;
        this.academicYearService = academicYearService;
        this.classGroupService = classGroupService;
        this.schoolClassService = schoolClassService;
        this.trainingSystemService = trainingSystemService;
        this.rowLevel = rowLevel;
    }

    public Page<StudentEnrollment> list(ListParams params) {
        return enrollments.findAll(
                rowLevel.scope(StudentEnrollment.class, Action.READ).and(StudentEnrollmentSpecifications.from(params)),
                params.pageable(SORTABLE, DEFAULT_SORT));
    }

    public StudentEnrollment get(Long id) {
        return enrollments.findOne(rowLevel.byId(StudentEnrollment.class, Action.READ, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    public List<StudentEnrollment> getMany(List<Long> ids) {
        return enrollments.findAll(rowLevel.byIds(StudentEnrollment.class, Action.READ, ids));
    }

    // ---- create / update / delete

    @Transactional
    public StudentEnrollment create(StudentRequest request) {
        AcademicYear year = academicYearService.get(request.academicYear().id());
        SchoolClass schoolClass = classOfYear(request.schoolClass().id(), year);
        TrainingSystem system = request.trainingSystem() == null || request.trainingSystem().id() == null
                ? null
                : trainingSystemOf(request.trainingSystem().id(), year);

        String fullName = request.fullName().trim();
        String code = blankToNull(request.studentCode());
        if (code == null) {
            code = nextCode(year, schoolClass);
        }
        requireCodeAvailable(year, code, null);

        Student student = new Student(year.getOrganization(), code, fullName);
        copyStudentFields(student, request);
        requirePersonalIdAvailable(year, student.getPersonalId(), null);
        students.persist(student);

        StudentEnrollment row = new StudentEnrollment(student, year);
        row.setSchoolClass(schoolClass);
        row.setTrainingSystem(system);
        row.setTransferInDate(request.transferInDate());
        row.setNote(blankToNull(request.note()));

        StudentEnrollment saved = enrollments.persist(row);
        // Flushed first: the identity key only exists afterwards, and checking a
        // policy against a half-built row is wrong in every case.
        enrollments.flush();
        rowLevel.checkWritable(saved);
        return saved;
    }

    @Transactional
    public StudentEnrollment update(Long id, StudentPatchRequest request) {
        StudentEnrollment row = enrollments.findOne(rowLevel.byId(StudentEnrollment.class, Action.WRITE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        Student student = row.getStudent();
        AcademicYear year = row.getAcademicYear();

        // Everything that can be refused is checked before the entity is touched,
        // so the auto-flush cannot push the update ahead of the check and let a
        // unique index answer instead.
        String code = request.studentCode() == null ? null : blankToNull(request.studentCode());
        if (request.studentCode() != null) {
            if (code == null) {
                throw invalid("studentCode", "Vui lòng nhập mã học sinh");
            }
            requireCodeAvailable(year, code, student.getId());
        }
        if (request.personalId() != null) {
            requirePersonalIdAvailable(year, blankToNull(request.personalId()), student.getId());
        }
        SchoolClass schoolClass = request.schoolClass() == null || request.schoolClass().id() == null
                ? null
                : classOfYear(request.schoolClass().id(), year);
        boolean systemTouched = request.trainingSystem() != null;
        TrainingSystem system = systemTouched && request.trainingSystem().id() != null
                ? trainingSystemOf(request.trainingSystem().id(), year)
                : null;

        if (code != null) {
            student.setStudentCode(code);
        }
        patchStudentFields(student, request);
        if (schoolClass != null) {
            row.setSchoolClass(schoolClass);
        }
        if (systemTouched) {
            row.setTrainingSystem(system);
        }
        if (request.transferInDate() != null) {
            row.setTransferInDate(request.transferInDate());
        }
        if (request.note() != null) {
            row.setNote(blankToNull(request.note()));
        }

        rowLevel.checkWritable(row);
        return enrollments.persist(row);
    }

    /**
     * Removes the student from the year. The student record goes with it when
     * that was their only year; otherwise it stays, with the others.
     */
    @Transactional
    public StudentEnrollment delete(Long id) {
        StudentEnrollment row = enrollments.findOne(rowLevel.byId(StudentEnrollment.class, Action.DELETE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        Student student = row.getStudent();
        enrollments.remove(row);
        enrollments.flush();
        if (enrollments.countByStudentId(student.getId()) == 0) {
            students.remove(student);
        }
        return row;
    }

    // ---- the class tree

    /** The khối lớp and lớp of a year with how many students each holds. */
    public StudentStructureResponse structure(Long academicYearId) {
        AcademicYear year = academicYearService.get(academicYearId);

        ListParams yearFilter = listOf("academicYear.id", String.valueOf(year.getId()));
        List<ClassGroup> groups = classGroupService.list(yearFilter).getContent();

        List<StudentEnrollment> inYear = enrollments.findAll(
                rowLevel.scope(StudentEnrollment.class, Action.READ)
                        .and(StudentEnrollmentSpecifications.from(yearFilter)));
        Map<Long, Long> perClass = new HashMap<>();
        long unassigned = 0;
        for (StudentEnrollment row : inYear) {
            if (row.getSchoolClass() == null) {
                unassigned++;
            } else {
                perClass.merge(row.getSchoolClass().getId(), 1L, Long::sum);
            }
        }

        List<StudentStructureResponse.Group> tree = new ArrayList<>();
        for (ClassGroup group : groups) {
            List<StudentStructureResponse.Klass> classes = schoolClassService
                    .list(listOf("classGroup.id", String.valueOf(group.getId())))
                    .getContent().stream()
                    .map(c -> new StudentStructureResponse.Klass(c.getId(), c.getName(), perClass.getOrDefault(c.getId(), 0L)))
                    .toList();
            long count = classes.stream().mapToLong(StudentStructureResponse.Klass::studentCount).sum();
            tree.add(new StudentStructureResponse.Group(group.getId(), group.getName(), count, classes));
        }
        return new StudentStructureResponse(inYear.size(), unassigned, tree);
    }

    // ---- bulk actions

    /** Puts the chosen students into one class of their year, from the given date. */
    @Transactional
    public int assignClass(AssignClassRequest request) {
        Set<Long> ids = new LinkedHashSet<>(request.ids());
        List<StudentEnrollment> rows = enrollments.findAll(rowLevel.byIds(StudentEnrollment.class, Action.WRITE, ids));
        if (rows.size() != ids.size()) {
            throw new ResourceNotFoundException(RESOURCE, ids.iterator().next());
        }
        Set<Long> yearIds = rows.stream().map(r -> r.getAcademicYear().getId()).collect(Collectors.toSet());
        if (yearIds.size() != 1) {
            throw invalid("ids", "Các học sinh được chọn phải thuộc cùng một năm học");
        }
        AcademicYear year = rows.get(0).getAcademicYear();
        SchoolClass schoolClass = classOfYear(request.schoolClassId(), year);

        for (StudentEnrollment row : rows) {
            row.setSchoolClass(schoolClass);
            row.setTransferInDate(request.transferInDate());
            rowLevel.checkWritable(row);
            enrollments.persist(row);
        }
        return rows.size();
    }

    /**
     * Carries the chosen students of an earlier year into a later one, with no
     * class — somebody places them afterwards. Those already enrolled in the later
     * year are skipped, so running it twice does no harm.
     */
    @Transactional
    public int importFromYear(ImportStudentsRequest request) {
        AcademicYear source = academicYearService.get(request.sourceAcademicYearId());
        AcademicYear target = academicYearService.get(request.targetAcademicYearId());
        if (!source.getOrganization().getId().equals(target.getOrganization().getId())) {
            throw invalid("targetAcademicYearId", "Hai năm học phải thuộc cùng một trường");
        }
        if (!source.getStartDate().isBefore(target.getStartDate())) {
            throw invalid("sourceAcademicYearId", "Năm học nguồn phải trước năm học đích");
        }

        Set<Long> ids = new LinkedHashSet<>(request.ids());
        List<StudentEnrollment> rows = enrollments.findAll(
                rowLevel.byIds(StudentEnrollment.class, Action.READ, ids)
                        .and((root, query, cb) -> cb.equal(root.get("academicYear").get("id"), source.getId())));
        if (rows.size() != ids.size()) {
            throw invalid("ids", "Có học sinh không thuộc năm học nguồn");
        }

        int imported = 0;
        for (StudentEnrollment from : rows) {
            if (enrollments.findByAcademicYearIdAndStudentId(target.getId(), from.getStudent().getId()).isPresent()) {
                continue;
            }
            StudentEnrollment row = new StudentEnrollment(from.getStudent(), target);
            row.setTrainingSystem(from.getTrainingSystem());
            row.setSourceEnrollment(from);
            enrollments.persist(row);
            enrollments.flush();
            rowLevel.checkWritable(row);
            imported++;
        }
        return imported;
    }

    // ---- helpers

    private SchoolClass classOfYear(Long classId, AcademicYear year) {
        SchoolClass schoolClass = schoolClassService.get(classId);
        if (!schoolClass.getClassGroup().getAcademicYear().getId().equals(year.getId())) {
            throw invalid("schoolClass", "Lớp không thuộc năm học " + year.getName());
        }
        return schoolClass;
    }

    private TrainingSystem trainingSystemOf(Long id, AcademicYear year) {
        TrainingSystem system = trainingSystemService.get(id);
        if (!system.getOrganization().getId().equals(year.getOrganization().getId())) {
            throw invalid("trainingSystem", "Hệ đào tạo không thuộc trường của năm học");
        }
        return system;
    }

    /** {@code NBK25-10A1-03}: the school, the year's first two-digit year, the class and a running number. */
    private String nextCode(AcademicYear year, SchoolClass schoolClass) {
        String prefix = "%s%02d-%s-".formatted(
                year.getOrganization().getCode(), year.getStartDate().getYear() % 100, schoolClass.getName());
        for (int n = 1; ; n++) {
            String candidate = prefix + "%02d".formatted(n);
            if (!students.existsByOrganizationIdAndStudentCode(year.getOrganization().getId(), candidate)) {
                return candidate;
            }
        }
    }

    private void requireCodeAvailable(AcademicYear year, String code, Long selfId) {
        students.findByOrganizationIdAndStudentCode(year.getOrganization().getId(), code).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ConflictException("Mã học sinh \"%s\" đã tồn tại".formatted(code));
            }
        });
    }

    private void requirePersonalIdAvailable(AcademicYear year, String personalId, Long selfId) {
        if (personalId == null) {
            return;
        }
        students.findByOrganizationIdAndPersonalId(year.getOrganization().getId(), personalId).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ConflictException("Số định danh \"%s\" đã tồn tại".formatted(personalId));
            }
        });
    }

    private static void copyStudentFields(Student s, StudentRequest r) {
        s.setDateOfBirth(r.dateOfBirth());
        s.setGender(r.gender());
        s.setPersonalId(blankToNull(r.personalId()));
        s.setPassportNumber(blankToNull(r.passportNumber()));
        s.setStatisticCode(blankToNull(r.statisticCode()));
        s.setAdmissionYear(r.admissionYear());
        s.setHealthInsuranceCode(blankToNull(r.healthInsuranceCode()));
        s.setProvince(blankToNull(r.province()));
        s.setDistrict(blankToNull(r.district()));
        s.setWard(blankToNull(r.ward()));
        s.setArea(blankToNull(r.area()));
        s.setHouseNumber(blankToNull(r.houseNumber()));
        s.setHamlet(blankToNull(r.hamlet()));
        s.setAddress(blankToNull(r.address()));
        s.setGuardianName(blankToNull(r.guardianName()));
        s.setGuardianPhone(blankToNull(r.guardianPhone()));
        s.setGuardianEmail(blankToNull(r.guardianEmail()));
        s.setBankName(blankToNull(r.bankName()));
        s.setBankAccountHolder(blankToNull(r.bankAccountHolder()));
        s.setBankAccountNumber(blankToNull(r.bankAccountNumber()));
        s.setNeedsInvoice(Boolean.TRUE.equals(r.needsInvoice()));
    }

    /** {@code null} leaves a field alone; a blank optional string clears it. */
    private static void patchStudentFields(Student s, StudentPatchRequest r) {
        if (r.fullName() != null) {
            s.setFullName(r.fullName().trim());
        }
        if (r.dateOfBirth() != null) {
            s.setDateOfBirth(r.dateOfBirth());
        }
        if (r.gender() != null) {
            s.setGender(r.gender());
        }
        if (r.personalId() != null) {
            s.setPersonalId(blankToNull(r.personalId()));
        }
        if (r.passportNumber() != null) {
            s.setPassportNumber(blankToNull(r.passportNumber()));
        }
        if (r.statisticCode() != null) {
            s.setStatisticCode(blankToNull(r.statisticCode()));
        }
        if (r.admissionYear() != null) {
            s.setAdmissionYear(r.admissionYear());
        }
        if (r.healthInsuranceCode() != null) {
            s.setHealthInsuranceCode(blankToNull(r.healthInsuranceCode()));
        }
        if (r.province() != null) {
            s.setProvince(blankToNull(r.province()));
        }
        if (r.district() != null) {
            s.setDistrict(blankToNull(r.district()));
        }
        if (r.ward() != null) {
            s.setWard(blankToNull(r.ward()));
        }
        if (r.area() != null) {
            s.setArea(blankToNull(r.area()));
        }
        if (r.houseNumber() != null) {
            s.setHouseNumber(blankToNull(r.houseNumber()));
        }
        if (r.hamlet() != null) {
            s.setHamlet(blankToNull(r.hamlet()));
        }
        if (r.address() != null) {
            s.setAddress(blankToNull(r.address()));
        }
        if (r.guardianName() != null) {
            s.setGuardianName(blankToNull(r.guardianName()));
        }
        if (r.guardianPhone() != null) {
            s.setGuardianPhone(blankToNull(r.guardianPhone()));
        }
        if (r.guardianEmail() != null) {
            s.setGuardianEmail(blankToNull(r.guardianEmail()));
        }
        if (r.bankName() != null) {
            s.setBankName(blankToNull(r.bankName()));
        }
        if (r.bankAccountHolder() != null) {
            s.setBankAccountHolder(blankToNull(r.bankAccountHolder()));
        }
        if (r.bankAccountNumber() != null) {
            s.setBankAccountNumber(blankToNull(r.bankAccountNumber()));
        }
        if (r.needsInvoice() != null) {
            s.setNeedsInvoice(r.needsInvoice());
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ValidationFailedException invalid(String field, String message) {
        return new ValidationFailedException("Dữ liệu không hợp lệ", Map.of(field, message));
    }

    /** An unpaged query with one filter, for reading a feature's list through its own service. */
    private static ListParams listOf(String field, String value) {
        LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add(field, value);
        query.add("_start", "0");
        query.add("_end", "1000");
        return new ListParams(query);
    }
}
