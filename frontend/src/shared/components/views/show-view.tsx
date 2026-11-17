"use client";

import type { PropsWithChildren } from "react";

import {
  useNavigation,
  useResourceParams,
} from "@refinedev/core";
import { RefreshButton } from "@/shared/components/buttons/refresh";
import { cn } from "@/shared/lib/utils";
import { PageActions, PageTitle } from "@/shared/components/layout/page-slots";
import { DeleteButton } from "@/shared/components/buttons/delete";
import { EditButton } from "@/shared/components/buttons/edit";

type ShowViewProps = PropsWithChildren<{
  className?: string;
}>;

export function ShowView({ children, className }: ShowViewProps) {
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

type ShowViewHeaderProps = PropsWithChildren<{
  resource?: string;
  title?: string;
  wrapperClassName?: string;
  headerClassName?: string;
}>;

export const ShowViewHeader = ({
  resource: resourceFromProps,
  title: titleFromProps,
}: ShowViewHeaderProps) => {
  const { list } = useNavigation();


  const { resource, identifier } = useResourceParams({
    resource: resourceFromProps,
  });
  const { id: recordItemId } = useResourceParams();

  const resourceName = resource?.name ?? identifier;

  const title =
    titleFromProps ??
    String(resource?.meta?.label ?? identifier ?? resource?.name ?? "");

  return (
    <>
      {/* The name is in the top bar, with the actions; see page-slots.tsx. */}
      <PageTitle>
        <h1 className="min-w-0 truncate text-xl font-semibold leading-7 tracking-tight">{title}</h1>
      </PageTitle>
      <PageActions>
      <RefreshButton
        variant="outline"
        recordItemId={recordItemId}
        resource={resourceName}
      />
      {/*
        Edit is the page's primary action and carries the only solid fill
        in the cluster. Before this, Refresh and Edit were both outlines
        and Delete was a solid red — which made the loudest control on a
        detail screen the one that destroys the record being read.
      */}
      <EditButton recordItemId={recordItemId} resource={resourceName} />
      {/*
        Unlike the one in a list row, this delete has to navigate. The
        screen it sits on is *about* the record it just removed, so staying
        put would leave the detail of a row that no longer exists — and the
        refetch that follows the invalidation would turn it into a 404.
      */}
      <DeleteButton
        variant="outline"
        className="text-destructive hover:bg-destructive hover:text-destructive-foreground"
        recordItemId={recordItemId}
        resource={resourceName}
        onSuccess={() => {
          if (resourceName) list(resourceName);
        }}
      />
      </PageActions>
    </>
  );
};

ShowView.displayName = "ShowView";
