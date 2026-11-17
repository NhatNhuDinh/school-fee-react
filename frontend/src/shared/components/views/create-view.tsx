"use client";

import { cn } from "@/shared/lib/utils";
import { PageTitle } from "@/shared/components/layout/page-slots";
import {
  useResourceParams,
} from "@refinedev/core";
import type { PropsWithChildren } from "react";

type CreateViewProps = PropsWithChildren<{
  className?: string;
}>;

export function CreateView({ children, className }: CreateViewProps) {
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

type CreateHeaderProps = PropsWithChildren<{
  resource?: string;
  title?: string;
  wrapperClassName?: string;
  headerClassName?: string;
}>;

export const CreateViewHeader = ({
  resource: resourceFromProps,
  title: titleFromProps,
}: CreateHeaderProps) => {


  const { resource, identifier } = useResourceParams({
    resource: resourceFromProps,
  });

  // Singular, like `ShowViewHeader` and unlike `ListViewHeader`: this screen is
  // about the one record being written, not about the collection. The plural it
  // used to ask for went unnoticed only because no page rendered this header.
  const title =
    titleFromProps ??
    String(resource?.meta?.label ?? identifier ?? resource?.name ?? "");

  return (
    <>
      {/* The name is in the top bar, with the actions; see page-slots.tsx. */}
      <PageTitle>
        <h1 className="min-w-0 truncate text-xl font-semibold leading-7 tracking-tight">{title}</h1>
      </PageTitle>
    </>
  );
};

CreateView.displayName = "CreateView";
