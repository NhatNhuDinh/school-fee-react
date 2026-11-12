"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { useLocation, useNavigate } from "react-router";

import { useAcademicYear } from "./academic-year-context";

/** `/academic_years/edit/9`, `/academic_years/show/9` -- a screen about one year. */
const YEAR_SCREEN = /^\/academic_years\/(edit|show)\/\d+$/;

/**
 * Makes everything on screen follow the year chosen in the header.
 *
 * Choosing another year only changes what the context holds, and nothing on the
 * page is listening to that by itself: a list keeps the rows of the year it was
 * opened under, which is how the old application's "everything follows the
 * year" rule was being broken. Two things happen when the choice moves:
 *
 * - every data query is invalidated, so whatever is mounted refetches under the
 *   new year -- screens read the year from `useAcademicYear`, not from a prop,
 *   so there is nothing for them to be told;
 * - a screen that is *about* one year (its edit or show page) is moved to the
 *   same screen for the new one, since refetching would only redraw the old year.
 *
 * Nothing happens on the first load -- the year being restored from storage or
 * defaulted is not a change -- nor when a school has no year at all.
 */
export function AcademicYearSync() {
  const { selected } = useAcademicYear();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const previousId = useRef<number | null>(null);

  const id = selected?.id ?? null;

  useEffect(() => {
    const before = previousId.current;
    previousId.current = id;
    if (before === null || id === null || before === id) return;

    // Refine keys everything it fetches for a resource under "data".
    void queryClient.invalidateQueries({
      predicate: (query) => query.queryKey[0] === "data",
    });

    const screen = YEAR_SCREEN.exec(pathname);
    if (screen) navigate(`/academic_years/${screen[1]}/${id}`, { replace: true });
  }, [id, pathname, navigate, queryClient]);

  return null;
}
