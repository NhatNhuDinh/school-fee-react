package com.dth.frw.module.school.academic.classgroup.repo;

import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.shared.web.ListParams;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates the parsed query string into a {@link Specification}: {@code id},
 * {@code name} and {@code organization.id}, plus {@code q} across the name and
 * the school's name. Unknown fields are ignored rather than rejected.
 */
public final class ClassGroupSpecifications {

    private ClassGroupSpecifications() {
    }

    public static Specification<ClassGroup> from(ListParams params) {
        List<Specification<ClassGroup>> specs = new ArrayList<>();

        for (ListParams.Criterion criterion : params.filters()) {
            Specification<ClassGroup> spec = switch (criterion.field()) {
                case "id" -> idSpec(criterion);
                case "name" -> nameSpec(criterion);
                case "organization.id", "organizationId" -> organizationSpec(criterion);
                case "academicYear.id", "academicYearId" -> academicYearSpec(criterion);
                default -> null;
            };
            if (spec != null) {
                specs.add(spec);
            }
        }

        params.search().ifPresent(term -> specs.add((root, query, cb) -> {
            String pattern = ListParams.likePattern(term);
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("organization").get("name")), pattern));
        }));

        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            specs.add((root, query, cb) -> root.get("id").in(ids));
        }

        return Specification.allOf(specs);
    }

    private static Specification<ClassGroup> idSpec(ListParams.Criterion criterion) {
        Long id = parseLong(criterion.value());
        if (id == null) {
            return never();
        }
        return criterion.operator() == ListParams.Operator.NE
                ? (root, query, cb) -> cb.notEqual(root.get("id"), id)
                : (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    private static Specification<ClassGroup> nameSpec(ListParams.Criterion criterion) {
        String value = criterion.value();
        return switch (criterion.operator()) {
            case LIKE -> (root, query, cb) ->
                    cb.like(cb.lower(root.get("name")), ListParams.likePattern(value));
            case NE -> (root, query, cb) -> cb.notEqual(root.get("name"), value);
            default -> (root, query, cb) -> cb.equal(root.get("name"), value);
        };
    }

    private static Specification<ClassGroup> organizationSpec(ListParams.Criterion criterion) {
        Long organizationId = parseLong(criterion.value());
        if (organizationId == null) {
            return never();
        }
        return criterion.operator() == ListParams.Operator.NE
                ? (root, query, cb) -> cb.notEqual(root.get("organization").get("id"), organizationId)
                : (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

    private static Specification<ClassGroup> academicYearSpec(ListParams.Criterion criterion) {
        Long yearId = parseLong(criterion.value());
        if (yearId == null) {
            return never();
        }
        return criterion.operator() == ListParams.Operator.NE
                ? (root, query, cb) -> cb.notEqual(root.get("academicYear").get("id"), yearId)
                : (root, query, cb) -> cb.equal(root.get("academicYear").get("id"), yearId);
    }

    private static Specification<ClassGroup> never() {
        return (root, query, cb) -> cb.disjunction();
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
