package com.dth.frw.module.school.tenant.service;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.TenantOwned;
import com.dth.frw.module.security.rowlevel.service.RowLevelPrincipalResolver;
import com.dth.frw.shared.security.rowlevel.Action;
import com.dth.frw.shared.security.rowlevel.RowLevelPolicySet.DesignTimeFilter;
import com.dth.frw.shared.security.rowlevel.RowLevelPrincipal;
import com.dth.frw.shared.security.rowlevel.Tri;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * The tenant boundary: a caller reaches the rows of their own school and no
 * other.
 *
 * <p>A school is an organization, and the caller's school is the organization
 * on their account. That is read per request from the authority cache, never
 * from the token, so moving somebody to another school takes effect on their
 * next request.
 *
 * <p>Three cases, and the order matters:
 * <ul>
 *   <li>a system administrator ({@value #SYSTEM_ADMIN_ROLE}) is not narrowed,
 *       because administering every school is what that role is for;
 *   <li>anybody else placed in a school sees that school's rows;
 *   <li>anybody else placed nowhere sees nothing. Falling back to "not
 *       narrowed" for an account with no school would turn a missing placement
 *       into a leak, so it fails closed instead.
 * </ul>
 *
 * <p>Declared as design-time <em>filters</em>, which attach to no role and
 * therefore cannot be escaped by holding a different one, and which apply to
 * reads, to the saved state of a write (so a row cannot be moved into another
 * school) and to deletes.
 */
@Component
public class TenantFilters {

    /** The one role that is not scoped to a school. */
    public static final String SYSTEM_ADMIN_ROLE = "ADMIN";

    private final RowLevelPrincipalResolver principals;

    public TenantFilters(RowLevelPrincipalResolver principals) {
        this.principals = principals;
    }

    /** One filter per action, for a resource whose entity names its school. */
    public <T extends TenantOwned> List<DesignTimeFilter<T>> forEveryAction() {
        return Arrays.stream(Action.values()).map(this::<T>filterFor).toList();
    }

    /**
     * The same boundary for the school record itself: somebody placed in a school
     * reads that school's organization and no other. Without it the organization
     * list would name every school to every school.
     */
    public List<DesignTimeFilter<Organization>> forSchools() {
        return Arrays.stream(Action.values())
                .map(action -> new DesignTimeFilter<Organization>(
                        action,
                        "Chỉ trường của mình",
                        (root, query, cb) -> principals.current()
                                .map(principal -> {
                                    if (isSystemAdmin(principal)) {
                                        return cb.conjunction();
                                    }
                                    if (principal.organizationId() == null) {
                                        return cb.disjunction();
                                    }
                                    return cb.equal(root.get("id"), principal.organizationId());
                                })
                                .orElseGet(cb::conjunction),
                        (entity, principal) -> {
                            if (isSystemAdmin(principal)) {
                                return Tri.TRUE;
                            }
                            if (principal.organizationId() == null) {
                                return Tri.FALSE;
                            }
                            return principal.organizationId().equals(entity.getId()) ? Tri.TRUE : Tri.FALSE;
                        }))
                .toList();
    }

    private <T extends TenantOwned> DesignTimeFilter<T> filterFor(Action action) {
        return new DesignTimeFilter<>(
                action,
                "Chỉ trường của mình",
                using(),
                TenantFilters::check);
    }

    private <T extends TenantOwned> Specification<T> using() {
        return (root, query, cb) -> principals.current()
                .map(principal -> {
                    if (isSystemAdmin(principal)) {
                        return cb.conjunction();
                    }
                    if (principal.organizationId() == null) {
                        return cb.disjunction();
                    }
                    return cb.equal(root.get("organization").get("id"), principal.organizationId());
                })
                .orElseGet(cb::conjunction);
    }

    /** The in-memory half: the same decision against a row already loaded. */
    private static Tri check(TenantOwned entity, RowLevelPrincipal principal) {
        if (isSystemAdmin(principal)) {
            return Tri.TRUE;
        }
        if (principal.organizationId() == null) {
            return Tri.FALSE;
        }
        if (entity.getOrganization() == null || entity.getOrganization().getId() == null) {
            return Tri.UNKNOWN;
        }
        return entity.getOrganization().getId().equals(principal.organizationId()) ? Tri.TRUE : Tri.FALSE;
    }

    public static boolean isSystemAdmin(RowLevelPrincipal principal) {
        return principal.roleCodes().contains(SYSTEM_ADMIN_ROLE);
    }
}
