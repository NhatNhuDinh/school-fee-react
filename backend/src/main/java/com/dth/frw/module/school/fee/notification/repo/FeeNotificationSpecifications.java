package com.dth.frw.module.school.fee.notification.repo;

import com.dth.frw.domain.school.fee.FeeNotification;
import com.dth.frw.domain.school.fee.FeeNotificationStatus;
import com.dth.frw.shared.web.ListParams;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates the parsed query string into a {@link Specification}: {@code id},
 * {@code batch.id}, {@code status}, {@code academicYear.id} and {@code organization.id},
 * plus {@code q} across the subject. Unknown fields are ignored rather than rejected, and
 * {@code status=any} restricts nothing.
 */
public final class FeeNotificationSpecifications {

    private FeeNotificationSpecifications() {
    }

    public static Specification<FeeNotification> from(ListParams params) {
        List<Specification<FeeNotification>> specs = new ArrayList<>();
        for (ListParams.Criterion criterion : params.filters()) {
            Specification<FeeNotification> spec = switch (criterion.field()) {
                case "id" -> idSpec("id", criterion);
                case "batch.id", "batchId" -> refSpec("batch", criterion);
                case "organization.id", "organizationId" -> refSpec("organization", criterion);
                case "academicYear.id", "academicYearId" -> refSpec("academicYear", criterion);
                case "status" -> statusSpec(criterion);
                default -> null;
            };
            if (spec != null) {
                specs.add(spec);
            }
        }
        params.search().ifPresent(term -> specs.add((root, query, cb) ->
                cb.like(cb.lower(root.get("subject")), ListParams.likePattern(term))));
        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            specs.add((root, query, cb) -> root.get("id").in(ids));
        }
        return Specification.allOf(specs);
    }

    private static Specification<FeeNotification> statusSpec(ListParams.Criterion criterion) {
        String value = criterion.value().trim().toUpperCase();
        if (value.equals("ANY")) {
            return (root, query, cb) -> cb.conjunction();
        }
        try {
            FeeNotificationStatus status = FeeNotificationStatus.valueOf(value);
            return (root, query, cb) -> cb.equal(root.get("status"), status);
        } catch (IllegalArgumentException e) {
            return never();
        }
    }

    private static Specification<FeeNotification> idSpec(String field, ListParams.Criterion criterion) {
        Long id = parseLong(criterion.value());
        return id == null ? never() : (root, query, cb) -> cb.equal(root.get(field), id);
    }

    private static Specification<FeeNotification> refSpec(String attribute, ListParams.Criterion criterion) {
        Long id = parseLong(criterion.value());
        if (id == null) {
            return never();
        }
        return (root, query, cb) -> cb.equal(root.get(attribute).get("id"), id);
    }

    private static Specification<FeeNotification> never() {
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
