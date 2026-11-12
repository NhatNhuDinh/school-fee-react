package com.dth.frw.module.school.student.repo;

import com.dth.frw.domain.school.student.StudentEnrollment;
import com.dth.frw.shared.web.ListParams;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates the parsed query string into a {@link Specification}: {@code id},
 * {@code academicYear.id}, {@code schoolClass.id}, {@code classGroup.id},
 * {@code organization.id}, {@code student.fullName} and {@code student.studentCode},
 * plus two that are not columns — {@code unassigned=true} (no class yet) and
 * {@code notEnrolledIn=<year id>} (the student has no enrollment in that year,
 * which is what the import from a previous year lists) — and {@code q} across the
 * code, name, personal id and guardian phone. Unknown fields are ignored.
 */
public final class StudentEnrollmentSpecifications {

    private StudentEnrollmentSpecifications() {
    }

    public static Specification<StudentEnrollment> from(ListParams params) {
        List<Specification<StudentEnrollment>> specs = new ArrayList<>();

        for (ListParams.Criterion criterion : params.filters()) {
            Specification<StudentEnrollment> spec = switch (criterion.field()) {
                case "id" -> idSpec(root -> root.get("id"), criterion);
                case "academicYear.id", "academicYearId" ->
                        idSpec(root -> root.get("academicYear").get("id"), criterion);
                case "schoolClass.id", "schoolClassId" ->
                        idSpec(root -> root.get("schoolClass").get("id"), criterion);
                case "classGroup.id", "classGroupId" ->
                        idSpec(root -> root.get("schoolClass").get("classGroup").get("id"), criterion);
                case "organization.id", "organizationId" ->
                        idSpec(root -> root.get("organization").get("id"), criterion);
                case "student.fullName" -> textSpec(root -> root.get("student").get("fullName"), criterion);
                case "student.studentCode" -> textSpec(root -> root.get("student").get("studentCode"), criterion);
                case "unassigned" -> "true".equalsIgnoreCase(criterion.value().trim())
                        ? (root, query, cb) -> cb.isNull(root.get("schoolClass"))
                        : null;
                case "notEnrolledIn" -> notEnrolledIn(criterion);
                default -> null;
            };
            if (spec != null) {
                specs.add(spec);
            }
        }

        params.search().ifPresent(term -> specs.add((root, query, cb) -> {
            String pattern = ListParams.likePattern(term);
            Path<?> student = root.get("student");
            return cb.or(
                    cb.like(cb.lower(student.get("studentCode")), pattern),
                    cb.like(cb.lower(student.get("fullName")), pattern),
                    cb.like(cb.lower(student.get("personalId")), pattern),
                    cb.like(cb.lower(student.get("guardianPhone")), pattern));
        }));

        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            specs.add((root, query, cb) -> root.get("id").in(ids));
        }

        return Specification.allOf(specs);
    }

    private static Specification<StudentEnrollment> idSpec(
            Function<Root<StudentEnrollment>, Path<Object>> path, ListParams.Criterion criterion) {
        Long id = parseLong(criterion.value());
        if (id == null) {
            return (root, query, cb) -> cb.disjunction();
        }
        return criterion.operator() == ListParams.Operator.NE
                ? (root, query, cb) -> cb.notEqual(path.apply(root), id)
                : (root, query, cb) -> cb.equal(path.apply(root), id);
    }

    private static Specification<StudentEnrollment> textSpec(
            Function<Root<StudentEnrollment>, Path<Object>> path, ListParams.Criterion criterion) {
        String value = criterion.value();
        return switch (criterion.operator()) {
            case LIKE -> (root, query, cb) ->
                    cb.like(cb.lower(path.apply(root).as(String.class)), ListParams.likePattern(value));
            case NE -> (root, query, cb) -> cb.notEqual(path.apply(root), value);
            default -> (root, query, cb) -> cb.equal(path.apply(root), value);
        };
    }

    /** No enrollment of the same student in the given year. */
    private static Specification<StudentEnrollment> notEnrolledIn(ListParams.Criterion criterion) {
        Long yearId = parseLong(criterion.value());
        if (yearId == null) {
            return null;
        }
        return (root, query, cb) -> {
            var other = query.subquery(Long.class);
            var from = other.from(StudentEnrollment.class);
            other.select(from.get("id"))
                    .where(
                            cb.equal(from.get("academicYear").get("id"), yearId),
                            cb.equal(from.get("student"), root.get("student")));
            return cb.not(cb.exists(other));
        };
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
