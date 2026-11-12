/**
 * The `class_groups` resource (khối lớp) and its `school_classes` (lớp), as the
 * API reports them — `ClassGroupResponse` and `SchoolClassResponse` on the
 * backend. Kept in step by hand, like the rest of the API contract.
 */
export type ClassGroup = {
  id: number;
  organization: ClassGroupSchool;
  academicYear: ClassGroupYear;
  name: string;
  createdAt: string;
  updatedAt: string | null;
};

/** The school as a khối lớp reports it. A school is an organization. */
export type ClassGroupSchool = {
  id: number;
  code: string;
  name: string;
};

export type ClassGroupYear = {
  id: number;
  name: string;
};

export type SchoolClass = {
  id: number;
  classGroup: { id: number; name: string };
  name: string;
  createdAt: string;
  updatedAt: string | null;
};

/**
 * What the create form holds. The year decides the school, so there is no
 * school to pick. `academicYear.id` is an empty string until a year is chosen.
 */
export type ClassGroupFormValues = {
  academicYear: { id: string };
  name: string;
};
