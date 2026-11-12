import { useCustom } from "@refinedev/core";

import type { StudentStructure } from "@/domains/student/types";
import { API_URL } from "@/shared/api/constants";
import { customResult } from "@/shared/hooks/use-custom-result";

/**
 * The khối lớp and lớp of a year with their student counts. Read through
 * `useCustom` because it is not a list: the tree is one answer, not a page of
 * rows. `refetch` is what a dialog calls after it changes who is in which class.
 */
export function useStudentStructure(academicYearId: number | undefined) {
  const { result, query } = useCustom<StudentStructure>({
    url: `${API_URL}/students/structure`,
    method: "get",
    config: { query: { academicYearId } },
    queryOptions: { enabled: academicYearId !== undefined },
  });

  return {
    structure: customResult(result?.data, "groups"),
    isLoading: query.isLoading,
    refetch: query.refetch,
  };
}
