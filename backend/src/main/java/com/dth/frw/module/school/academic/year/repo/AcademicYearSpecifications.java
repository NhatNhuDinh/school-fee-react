package com.dth.frw.module.school.academic.year.repo;

import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.shared.web.ListParams;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates the parsed query string into a {@link Specification}: {@code id},
 * {@code name} and {@code organization.id}, plus {@code q} across the name and
 * the school's name. Unknown fields are ignored rather than rejected.
 */
public final class AcademicYearSpecifications {

    private AcademicYearSpecifications() {
    }

    public static Specification<AcademicYear> from(ListParams params) {
        List<Specification<AcademicYear>> specs = new ArrayList<>();

        for (ListParams.Criterion criterion : params.filters()) {
            Specification<AcademicYear> spec = switch (criterion.field()) {
                case "id" -> idSpec(criterion);
                case "name" -> nameSpec(criterion);
                case "organization.id", "organizationId" -> organizationSpec(criterion);
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

    private static Specification<AcademicYear> idSpec(ListParams.Criterion criterion) {
        Long id = parseLong(criterion.value());
        if (id == null) {
            return never();
        }
        return criterion.operator() == ListParams.Operator.NE
                ? (root, query, cb) -> cb.notEqual(root.get("id"), id)
                : (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    private static Specification<AcademicYear> nameSpec(ListParams.Criterion criterion) {
        String value = criterion.value();
        return switch (criterion.operator()) {
            case LIKE -> (root, query, cb) ->
                    cb.like(cb.lower(root.get("name")), ListParams.likePattern(value));
            case NE -> (root, query, cb) -> cb.notEqual(root.get("name"), value);
            default -> (root, query, cb) -> cb.equal(root.get("name"), value);
        };
    }

    private static Specification<AcademicYear> organizationSpec(ListParams.Criterion criterion) {
        Long organizationId = parseLong(criterion.value());
        if (organizationId == null) {
            return never();
        }
        return criterion.operator() == ListParams.Operator.NE
                ? (root, query, cb) -> cb.notEqual(root.get("organization").get("id"), organizationId)
                : (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

    private static Specification<AcademicYear> never() {
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
