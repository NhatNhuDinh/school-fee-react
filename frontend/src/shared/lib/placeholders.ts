/**
 * Menu entries carried over from the old application whose screens have not
 * been migrated yet.
 *
 * They are in the sidebar so the structure is the one people already know, and
 * each opens `ComingSoon` until its own phase builds the real screen. The names
 * are the resource names the real screens will use -- they become the API path
 * and the permission prefix (`fee_items:read`), so a screen that arrives later
 * takes over its entry by deleting the name from here and declaring a route.
 *
 * Read by three places that have to agree: `app/resources` (the menu entry),
 * `app/router/routes.tsx` (the placeholder route) and `shared/lib/permissions.ts`
 * (these have no permission codes yet, so they must not be hidden for lack of one).
 */
export const PLACEHOLDER_RESOURCES = ["fee_collection_payments"] as const;
