"use client";

import { useGetIdentity, useList } from "@refinedev/core";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";

import type { AcademicYear } from "@/domains/academic-year/types";

/**
 * The school year the person is working in.
 *
 * Everything a school does -- classes, students, fees, collections -- belongs to
 * a year, so every one of those screens reads the year from here rather than
 * asking for it. Somebody placed in a school picks one in the header and every
 * screen follows.
 *
 * It applies only to somebody who belongs to a school. A system administrator
 * belongs to none and works across all of them, so there is no single year for
 * them to work in: `enabled` is false and `selected` is null.
 */
type AcademicYearContextValue = {
  /** False for an account placed in no school; there is then nothing to pick. */
  enabled: boolean;
  /** The school's years, newest first. */
  years: AcademicYear[];
  /** The year in force, or null before years load or when the school has none. */
  selected: AcademicYear | null;
  select: (id: number) => void;
  isLoading: boolean;
};

const AcademicYearContext = createContext<AcademicYearContextValue>({
  enabled: false,
  years: [],
  selected: null,
  select: () => undefined,
  isLoading: false,
});

type SchoolIdentity = {
  username?: string;
  organization?: { id: number } | null;
};

/**
 * The key a choice is remembered under: per school and per account, so two
 * people sharing a browser, or one person moving between schools, never inherit
 * each other's year.
 */
function storageKey(identity: SchoolIdentity | undefined): string | null {
  if (!identity?.organization) return null;
  return `school-fee.academic-year.${identity.organization.id}.${identity.username ?? ""}`;
}

/** Storage can be blocked or full; remembering a choice is never worth a crash. */
function readStored(key: string | null): number | null {
  if (!key) return null;
  try {
    const raw = window.localStorage.getItem(key);
    const id = raw == null ? NaN : Number(raw);
    return Number.isFinite(id) ? id : null;
  } catch {
    return null;
  }
}

function writeStored(key: string | null, id: number) {
  if (!key) return;
  try {
    window.localStorage.setItem(key, String(id));
  } catch {
    // Not remembered; the choice still holds for this session.
  }
}

export function AcademicYearProvider({ children }: { children: React.ReactNode }) {
  const { data: identity } = useGetIdentity<SchoolIdentity>();
  const enabled = Boolean(identity?.organization);
  const key = storageKey(identity);

  const {
    result: { data },
    query: { isLoading },
  } = useList<AcademicYear>({
    resource: "academic_years",
    pagination: { currentPage: 1, pageSize: 100 },
    sorters: [{ field: "startDate", order: "desc" }],
    queryOptions: { enabled },
  });

  const years = useMemo(() => (enabled ? (data ?? []) : []), [enabled, data]);

  // The id the person chose, restored from where it was last remembered. It is
  // reconciled against the list below rather than trusted: a remembered year can
  // since have been deleted.
  const [chosenId, setChosenId] = useState<number | null>(() => readStored(key));

  // The account can change under a mounted provider (sign out, sign in as
  // somebody else), and the remembered choice belongs to the new account.
  useEffect(() => {
    setChosenId(readStored(key));
  }, [key]);

  const selected = useMemo(() => {
    if (years.length === 0) return null;
    return years.find((year) => year.id === chosenId) ?? years[0];
  }, [years, chosenId]);

  const select = useCallback(
    (id: number) => {
      setChosenId(id);
      writeStored(key, id);
    },
    [key]
  );

  const value = useMemo(
    () => ({ enabled, years, selected, select, isLoading }),
    [enabled, years, selected, select, isLoading]
  );

  return (
    <AcademicYearContext.Provider value={value}>
      {children}
    </AcademicYearContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAcademicYear(): AcademicYearContextValue {
  return useContext(AcademicYearContext);
}
