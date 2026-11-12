import type { ComponentProps } from "react";

import type { OrganizationStatus } from "@/domains/organization/types";
import type { UserStatus } from "@/domains/user/types";
import type { Badge } from "@/shared/ui/badge";

/**
 * Which badge every status is drawn in, for every resource, in one place.
 *
 * Out of `domains/`, where the statuses themselves are declared, because a
 * badge variant is a word from the screen's vocabulary rather than the data's:
 * the domain says an account may be `locked`, the UI decides that `locked` is
 * red. Together rather than one table per feature, because one screen regularly
 * shows another resource's status — an organization lists its departments — and
 * a feature may not import another feature.
 */
type BadgeVariant = NonNullable<ComponentProps<typeof Badge>["variant"]>;

/**
 * Only "active" is a working state, so it is the only green one. Locked is
 * destructive rather than merely muted because it means somebody decided this
 * account was a risk, which is worth noticing in a list; inactive is neutral,
 * because it means nothing more than "not in use".
 */
export const USER_STATUS_VARIANTS: Record<UserStatus, BadgeVariant> = {
  active: "success",
  inactive: "neutral",
  locked: "destructive",
};

/**
 * Only "active" is a live state, so it is the only one that reads as neutral.
 * Inactive is muted rather than destructive: retiring an organization is an
 * ordinary administrative decision, not a warning.
 */
export const ORGANIZATION_STATUS_VARIANTS: Record<
  OrganizationStatus,
  BadgeVariant
> = {
  active: "success",
  inactive: "neutral",
};

/**
 * How each action of a `resource:action` code reads in a badge. `delete` is the
 * only destructive one and the only one coloured as such; `read` stays quiet
 * because most codes are reads and colouring them all would be noise.
 *
 * Keyed by string rather than `PermissionAction`, because the action is split
 * out of a code the catalogue returned and nothing guarantees it is one of the
 * three.
 */
export const PERMISSION_ACTION_VARIANTS: Record<string, BadgeVariant> = {
  read: "outline",
  write: "secondary",
  delete: "destructive",
};
