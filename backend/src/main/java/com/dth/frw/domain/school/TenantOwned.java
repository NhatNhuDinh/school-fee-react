package com.dth.frw.domain.school;

import com.dth.frw.domain.catalog.organization.Organization;

/**
 * An entity that belongs to one school.
 *
 * <p>A school is an {@link Organization}: the tenant is the organization the
 * row sits under, and every school-owned table carries a non-null
 * {@code organization_id}. Isolation between schools is a row-level
 * {@code FILTER} over that column, declared once in
 * {@code TenantFilters} and attached to each resource's policy set.
 */
public interface TenantOwned {

    Organization getOrganization();
}
