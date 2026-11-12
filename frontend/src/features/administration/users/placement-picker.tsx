import { useMemo } from "react";
import { useCan, useList } from "@refinedev/core";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select";
import {
  FormControl,
  FormItem,
  FormLabel,
} from "@/shared/ui/form";
import { cn } from "@/shared/lib/utils";
import type { Organization } from "@/domains/organization/types";
import type { OrganizationSummary } from "@/domains/user/types";

/**
 * Which school an account belongs to.
 *
 * This is the whole of how a user is attached to a tenant: a school is an
 * organization, and the school filter on every school-owned resource compares
 * against the organization on the account. An account placed in no school sees
 * nothing of any school's data, which is why a school's staff must be placed
 * here before they can work.
 *
 * `NONE` is the sentinel the `Select` needs because Radix reserves the empty
 * string for "nothing selected"; it is translated back to `{ id: null }`, which
 * is how the API is told to clear a placement rather than leave it alone.
 */

export type PlacementRef = { id: number | null };

/** Radix `Select` treats `value=""` as unselected, so "none" needs a real value. */
const NONE = "__none__";

type PlacementPickerProps = {
  organization: PlacementRef | null | undefined;
  onOrganizationChange: (value: PlacementRef) => void;
  /**
   * The record's own school, on an edit form. Merged into the options so the
   * trigger shows the current value even before the list arrives — and even
   * when it never does, because reading schools is a separate grant.
   */
  currentOrganization?: OrganizationSummary | null;
  /** The wrapper around the field; a form laying out a grid passes `"contents"`. */
  className?: string;
  /** Applied to the field — a column span, on such a grid. */
  itemClassName?: string;
};

export function PlacementPicker({
  organization,
  onOrganizationChange,
  currentOrganization,
  className,
  itemClassName,
}: PlacementPickerProps) {
  // Reading schools is a separate grant from writing users, so asking anyway
  // would spend a request on a guaranteed 403 and raise a toast over the form.
  const { data: canReadOrganizations } = useCan({
    resource: "organizations",
    action: "list",
  });
  const mayReadOrganizations = canReadOrganizations?.can ?? false;

  const organizationId = organization?.id ?? null;

  const {
    result: organizationResult,
    query: { isLoading: organizationsLoading },
  } = useList<Organization>({
    resource: "organizations",
    pagination: { pageSize: 200 },
    sorters: [{ field: "name", order: "asc" }],
    queryOptions: { enabled: mayReadOrganizations },
  });

  // The record's own school, merged in rather than waited for: `Select` matches
  // its value against the items mounted at that moment and does not re-run the
  // match when items arrive later.
  const organizationOptions = useMemo(() => {
    const list = (organizationResult?.data ?? []).map((o) => ({
      value: String(o.id),
      label: o.name,
    }));
    if (!currentOrganization) return list;
    return list.some((o) => o.value === String(currentOrganization.id))
      ? list
      : [
          {
            value: String(currentOrganization.id),
            label: currentOrganization.name,
          },
          ...list,
        ];
  }, [organizationResult?.data, currentOrganization]);

  return (
    <div className={cn("flex flex-col gap-8", className)}>
      <FormItem className={itemClassName}>
        <FormLabel>Trường (đơn vị)</FormLabel>
        <Select
          onValueChange={(raw) =>
            onOrganizationChange({ id: raw === NONE ? null : Number(raw) })
          }
          value={organizationId == null ? NONE : String(organizationId)}
          disabled={!mayReadOrganizations}
        >
          <FormControl>
            <SelectTrigger className="w-full">
              <SelectValue
                placeholder={organizationsLoading ? "Đang tải…" : "Chưa gán trường"}
              />
            </SelectTrigger>
          </FormControl>
          <SelectContent>
            <SelectItem value={NONE}>Chưa gán trường</SelectItem>
            {organizationOptions.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </FormItem>
    </div>
  );
}
