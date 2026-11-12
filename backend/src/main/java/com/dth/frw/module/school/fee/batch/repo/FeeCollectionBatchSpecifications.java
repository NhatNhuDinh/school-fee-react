package com.dth.frw.module.school.fee.batch.repo;

import com.dth.frw.domain.school.fee.FeeCollectionBatch;
import com.dth.frw.domain.school.fee.FeeCollectionScope;
import com.dth.frw.shared.web.ListParams;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates the parsed query string into a {@link Specification}: {@code id},
 * {@code name}, {@code collectionMonth}, {@code scope}, {@code status},
 * {@code organization.id} and {@code academicYear.id}, plus {@code q} across the name.
 * Unknown fields are ignored rather than rejected.
 */
public final class FeeCollectionBatchSpecifications {

    private FeeCollectionBatchSpecifications() {
    }

    public static Specification<FeeCollectionBatch> from(ListParams params) {
        List<Specification<FeeCollectionBatch>> specs = new ArrayList<>();
        for (ListParams.Criterion criterion : params.filters()) {
            Specification<FeeCollectionBatch> spec = switch (criterion.field()) {
                case "id" -> longSpec("id", criterion);
                case "name" -> (root, query, cb) -> cb.like(cb.lower(root.get("name")), ListParams.likePattern(criterion.value()));
                case "collectionMonth" -> intSpec("collectionMonth", criterion);
                case "scope" -> scopeSpec(criterion);
                case "status" -> statusSpec(criterion);
                case "organization.id", "organizationId" -> refSpec("organization", criterion);
                case "academicYear.id", "academicYearId" -> refSpec("academicYear", criterion);
                default -> null;
            };
            if (spec != null) {
                specs.add(spec);
            }
        }
        params.search().ifPresent(term -> specs.add((root, query, cb) ->
                cb.like(cb.lower(root.get("name")), ListParams.likePattern(term))));
        List<Long> ids = params.ids();
        if (!ids.isEmpty()) {
            specs.add((root, query, cb) -> root.get("id").in(ids));
        }
        return Specification.allOf(specs);
    }

    /**
     * {@code NOT_STARTED}, {@code COLLECTING} or {@code COMPLETED}, the same reading of the
     * money the entity's own {@code status()} makes; {@code any} restricts nothing, which is
     * a value rather than an omission because a permanent filter cannot be taken away.
     */
    private static Specification<FeeCollectionBatch> statusSpec(ListParams.Criterion criterion) {
        String value = criterion.value().trim().toUpperCase();
        return switch (value) {
            case "ANY" -> (root, query, cb) -> cb.conjunction();
            case "NOT_STARTED" -> (root, query, cb) ->
                    cb.lessThanOrEqualTo(root.<BigDecimal>get("collectedAmount"), BigDecimal.ZERO);
            case "COMPLETED" -> (root, query, cb) -> cb.and(
                    cb.greaterThan(root.<BigDecimal>get("collectedAmount"), BigDecimal.ZERO),
                    cb.greaterThanOrEqualTo(root.<BigDecimal>get("collectedAmount"), root.<BigDecimal>get("receivableAmount")));
            case "COLLECTING" -> (root, query, cb) -> cb.and(
                    cb.greaterThan(root.<BigDecimal>get("collectedAmount"), BigDecimal.ZERO),
                    cb.lessThan(root.<BigDecimal>get("collectedAmount"), root.<BigDecimal>get("receivableAmount")));
            default -> never();
        };
    }

    private static Specification<FeeCollectionBatch> scopeSpec(ListParams.Criterion criterion) {
        try {
            FeeCollectionScope value = FeeCollectionScope.valueOf(criterion.value().trim());
            return (root, query, cb) -> cb.equal(root.get("scope"), value);
        } catch (IllegalArgumentException e) {
            return never();
        }
    }

    private static Specification<FeeCollectionBatch> longSpec(String field, ListParams.Criterion criterion) {
        Long id = parseLong(criterion.value());
        return id == null ? never() : (root, query, cb) -> cb.equal(root.get(field), id);
    }

    /** {@code any} restricts nothing: a permanent filter's value can change but it cannot be removed. */
    private static Specification<FeeCollectionBatch> intSpec(String field, ListParams.Criterion criterion) {
        if (criterion.value().trim().equalsIgnoreCase("any")) {
            return (root, query, cb) -> cb.conjunction();
        }
        Long value = parseLong(criterion.value());
        return value == null ? never() : (root, query, cb) -> cb.equal(root.get(field), value.intValue());
    }

    private static Specification<FeeCollectionBatch> refSpec(String attribute, ListParams.Criterion criterion) {
        Long id = parseLong(criterion.value());
        if (id == null) {
            return never();
        }
        return criterion.operator() == ListParams.Operator.NE
                ? (root, query, cb) -> cb.notEqual(root.get(attribute).get("id"), id)
                : (root, query, cb) -> cb.equal(root.get(attribute).get("id"), id);
    }

    private static Specification<FeeCollectionBatch> never() {
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
