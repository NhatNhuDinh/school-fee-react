"use client";

import { cn } from "@/shared/lib/utils";
import { PageActions, PageTitle } from "@/shared/components/layout/page-slots";
import {
  useBack,
  useResourceParams,
} from "@refinedev/core";
import type { PropsWithChildren } from "react";
import { Button } from "@/shared/ui/button";
import { RefreshButton } from "@/shared/components/buttons/refresh";
import { ArrowLeftIcon } from "lucide-react";

type EditViewProps = PropsWithChildren<{
  className?: string;
}>;

export function EditView({ children, className }: EditViewProps) {
  return (
    <div
      className={cn(
        "flex flex-col",
        "gap-4",
        // Screen entrance. Lives here rather than on a route wrapper so
        // it fires when a screen actually mounts — opening a route dialog
        // over a list leaves the list, and its animation, alone.
        "screen-enter screen-enter-stagger",
        className
      )}
    >
      {children}
    </div>
  );
}

type EditViewHeaderProps = PropsWithChildren<{
  resource?: string;
  title?: string;
  wrapperClassName?: string;
  headerClassName?: string;
  actionsSlot?: React.ReactNode;
}>;

export const EditViewHeader = ({
  resource: resourceFromProps,
  title: titleFromProps,
  actionsSlot,
}: EditViewHeaderProps) => {
  const back = useBack();


  const { resource, identifier } = useResourceParams({
    resource: resourceFromProps,
  });
  const { id: recordItemId } = useResourceParams();

  const resourceName = resource?.name ?? identifier;

  // Singular, like `ShowViewHeader` and unlike `ListViewHeader`: this screen is
  // about the one record being edited, not about the collection. The plural it
  // used to ask for went unnoticed only because no page rendered this header.
  const title =
    titleFromProps ??
    String(resource?.meta?.label ?? identifier ?? resource?.name ?? "");

  return (
    <>
      {/* The back arrow and the name are in the top bar, with the actions; see page-slots.tsx. */}
      <PageTitle>
        <Button data-print="hide" variant="ghost" size="icon" onClick={back} className="-ms-2 shrink-0">
          <ArrowLeftIcon className="h-4 w-4" />
        </Button>
        <h1 className="min-w-0 truncate text-xl font-semibold leading-7 tracking-tight">{title}</h1>
      </PageTitle>
      <PageActions>
      {actionsSlot}
      <RefreshButton
        variant="outline"
        recordItemId={recordItemId}
        resource={resourceName}
      />
      </PageActions>
    </>
  );
};

EditView.displayName = "EditView";
