import { useGetIdentity } from "@refinedev/core";
import { Navigate } from "react-router";

import { useAcademicYear } from "@/shared/components/academic-year/academic-year-context";
import { AcademicYearList } from "./list";

/**
 * Where "Năm học" in the menu lands.
 *
 * A school works in one year at a time and that year is already chosen in the
 * header, so the menu opens it rather than a table of years to pick from. With
 * no year yet it opens the form to add the first one. A system administrator
 * belongs to no school and has no year chosen, so they still get the list of
 * every school's years.
 */
export const AcademicYearEntry = () => {
  const { data: identity, isLoading: identityLoading } = useGetIdentity();
  const { enabled, selected, isLoading } = useAcademicYear();

  // `enabled` is only known once the identity is, and `selected` once the
  // years are: redirecting before then would send a school to the wrong place.
  if (identityLoading || (enabled && isLoading)) return null;
  if (!identity || !enabled) return <AcademicYearList />;

  return (
    <Navigate
      replace
      to={
        selected
          ? `/academic_years/edit/${selected.id}`
          : "/academic_years/create"
      }
    />
  );
};
