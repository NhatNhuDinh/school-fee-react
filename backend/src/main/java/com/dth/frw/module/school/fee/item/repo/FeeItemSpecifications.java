package com.dth.frw.module.school.fee.item.repo;

import com.dth.frw.domain.school.fee.FeeCollectionCycle;
import com.dth.frw.domain.school.fee.FeeItem;
import com.dth.frw.domain.school.fee.FeePriceMode;
import com.dth.frw.domain.school.fee.FeeScope;
import com.dth.frw.shared.web.ListParams;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates the parsed query string into a {@link Specification}: {@code id},
 * {@code name}, {@code active}, the three enums, {@code organization.id} and
 * {@code academicYear.id}, plus {@code q} across the name. Unknown fields are
 * ignored rather than rejected.
 */
public final class FeeItemSpecifications {

    private FeeItemSpecifications() {
    }

    public static Specification<FeeItem> from(ListParams params) {
        List<Specification<FeeItem>> specs = new ArrayList<>();

        for (ListParams.Criterion criterion : params.filters()) {
            Specification<FeeItem> spec = switch (criterion.field()) {
                case "id" -> idSpec(criterion);
                case "name" -> nameSpec(criterion);
                case "active" -> activeSpec(criterion);
                case "scope" -> enumSpec("scope", FeeScope.class, criterion);
                case "priceMode" -> enumSpec("priceMode", FeePriceMode.class, criterion);
                case "collectionCycle" -> enumSpec("collectionCycle", FeeCollectionCycle.class, criterion);
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

    private static Specification<FeeItem> idSpec(ListParams.Criterion criterion) {
        Long id = parseLong(criterion.value());
        if (id == null) {
            return never();
        }
        return criterion.operator() == ListParams.Operator.NE
                ? (root, query, cb) -> cb.notEqual(root.get("id"), id)
                : (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    private static Specification<FeeItem> nameSpec(ListParams.Criterion criterion) {
        String value = criterion.value();
        return switch (criterion.operator()) {
            case LIKE -> (root, query, cb) ->
                    cb.like(cb.lower(root.get("name")), ListParams.likePattern(value));
            case NE -> (root, query, cb) -> cb.notEqual(root.get("name"), value);
            default -> (root, query, cb) -> cb.equal(root.get("name"), value);
        };
    }

    /**
     * {@code active=true} / {@code active=false}; {@code active=any} restricts nothing;
     * anything else matches nothing.
     *
     * <p>{@code any} exists because the list's data provider can change a filter's value
     * but cannot take a permanent filter away once it was sent — so "show the stopped
     * ones too" has to be a value of the filter, not its absence.
     */
    private static Specification<FeeItem> activeSpec(ListParams.Criterion criterion) {
        String text = criterion.value().trim().toLowerCase();
        if (text.equals("any")) {
            return (root, query, cb) -> cb.conjunction();
        }
        if (!text.equals("true") && !text.equals("false")) {
            return never();
        }
        boolean value = Boolean.parseBoolean(text);
        return criterion.operator() == ListParams.Operator.NE
                ? (root, query, cb) -> cb.notEqual(root.get("active"), value)
                : (root, query, cb) -> cb.equal(root.get("active"), value);
    }

    private static Specification<FeeItem> refSpec(String attribute, ListParams.Criterion criterion) {
        Long id = parseLong(criterion.value());
        if (id == null) {
            return never();
        }
        return criterion.operator() == ListParams.Operator.NE
                ? (root, query, cb) -> cb.notEqual(root.get(attribute).get("id"), id)
                : (root, query, cb) -> cb.equal(root.get(attribute).get("id"), id);
    }

    /** An enum column by name; a value that is not one of its names matches nothing. */
    private static <E extends Enum<E>> Specification<FeeItem> enumSpec(
            String field, Class<E> type, ListParams.Criterion criterion) {
        E value;
        try {
            value = Enum.valueOf(type, criterion.value().trim());
        } catch (IllegalArgumentException e) {
            return never();
        }
        return criterion.operator() == ListParams.Operator.NE
                ? (root, query, cb) -> cb.notEqual(root.get(field), value)
                : (root, query, cb) -> cb.equal(root.get(field), value);
    }

    private static Specification<FeeItem> never() {
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
