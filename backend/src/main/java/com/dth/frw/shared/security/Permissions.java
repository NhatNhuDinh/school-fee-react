package com.dth.frw.shared.security;

/**
 * The permission codes seeded by {@code 005-seed-security.sql}, as constants.
 *
 * <p>These are compile-time constants so that {@code @PreAuthorize} can be
 * written as {@code hasAuthority(Permissions.USERS_WRITE)} — a typo becomes a
 * compile error instead of an endpoint that silently authorises nobody.
 *
 * <p>The code is {@code resource:action}, where the resource half is verbatim
 * the resource name the frontend declares in {@code routes.tsx}. That is not
 * cosmetic: it is what lets the admin UI's access control provider build the
 * code it needs from the resource and action it is already handed, rather than
 * carrying a second copy of this mapping that could drift.
 *
 * <p>Three actions cover the six Refine actions: {@code list}/{@code show} need
 * {@code read}, {@code create}/{@code edit}/{@code clone} need {@code write},
 * and {@code delete} needs {@code delete}.
 */
public final class Permissions {

    public static final String USERS_READ = "users:read";
    public static final String USERS_WRITE = "users:write";
    public static final String USERS_DELETE = "users:delete";

    public static final String ROLES_READ = "roles:read";
    public static final String ROLES_WRITE = "roles:write";
    public static final String ROLES_DELETE = "roles:delete";

    public static final String PERMISSIONS_READ = "permissions:read";

    public static final String ORGANIZATIONS_READ = "organizations:read";
    public static final String ORGANIZATIONS_WRITE = "organizations:write";
    public static final String ORGANIZATIONS_DELETE = "organizations:delete";

    public static final String ACADEMIC_YEARS_READ = "academic_years:read";
    public static final String ACADEMIC_YEARS_WRITE = "academic_years:write";
    public static final String ACADEMIC_YEARS_DELETE = "academic_years:delete";

    public static final String TRAINING_SYSTEMS_READ = "training_systems:read";
    public static final String TRAINING_SYSTEMS_WRITE = "training_systems:write";
    public static final String TRAINING_SYSTEMS_DELETE = "training_systems:delete";

    public static final String MEASUREMENT_UNITS_READ = "measurement_units:read";
    public static final String MEASUREMENT_UNITS_WRITE = "measurement_units:write";
    public static final String MEASUREMENT_UNITS_DELETE = "measurement_units:delete";

    public static final String CLASS_GROUPS_READ = "class_groups:read";
    public static final String CLASS_GROUPS_WRITE = "class_groups:write";
    public static final String CLASS_GROUPS_DELETE = "class_groups:delete";

    public static final String SCHOOL_CLASSES_READ = "school_classes:read";
    public static final String SCHOOL_CLASSES_WRITE = "school_classes:write";
    public static final String SCHOOL_CLASSES_DELETE = "school_classes:delete";

    public static final String PRIORITY_CATEGORIES_READ = "priority_categories:read";
    public static final String PRIORITY_CATEGORIES_WRITE = "priority_categories:write";
    public static final String PRIORITY_CATEGORIES_DELETE = "priority_categories:delete";

    public static final String STUDENTS_READ = "students:read";
    public static final String STUDENTS_WRITE = "students:write";
    public static final String STUDENTS_DELETE = "students:delete";

    public static final String FEE_ITEMS_READ = "fee_items:read";
    public static final String FEE_ITEMS_WRITE = "fee_items:write";
    public static final String FEE_ITEMS_DELETE = "fee_items:delete";

    // No delete: unticking a cell is the unregistration.
    public static final String FEE_REGISTRATIONS_READ = "fee_registrations:read";
    public static final String FEE_REGISTRATIONS_WRITE = "fee_registrations:write";

    // Delete is for a đợt thu nothing has been collected for.
    public static final String FEE_COLLECTION_BATCHES_READ = "fee_collection_batches:read";
    public static final String FEE_COLLECTION_BATCHES_WRITE = "fee_collection_batches:write";
    public static final String FEE_COLLECTION_BATCHES_DELETE = "fee_collection_batches:delete";
    public static final String FEE_NOTIFICATIONS_READ = "fee_notifications:read";
    public static final String FEE_NOTIFICATIONS_WRITE = "fee_notifications:write";
    public static final String FEE_NOTIFICATIONS_DELETE = "fee_notifications:delete";

    // ADMIN only, and 016 spells that out rather than joining: editing a data
    // scope is editing the security model, so it is not a grant to hand around.
    // There is deliberately no row-level scope on the policy table itself --
    // deciding which policies you may see would need the policies you may see.
    public static final String ROW_LEVEL_POLICIES_READ = "row_level_policies:read";
    public static final String ROW_LEVEL_POLICIES_WRITE = "row_level_policies:write";
    public static final String ROW_LEVEL_POLICIES_DELETE = "row_level_policies:delete";

    // Granted to every system role, unlike everything above. They gate the bell,
    // not access to anybody else's rows: every notification endpoint is scoped
    // to the caller. There is no notifications:write -- nobody edits a
    // notification, the application raises one, and the endpoint that raises one
    // for somebody else needs USERS_WRITE because that is what it really does.
    public static final String NOTIFICATIONS_READ = "notifications:read";
    public static final String NOTIFICATIONS_DELETE = "notifications:delete";

    private Permissions() {
    }
}
