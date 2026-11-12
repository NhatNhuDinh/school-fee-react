/**
 * The `students` resource as the API reports it — `StudentResponse` on the
 * backend. A row is a student in one school year (an enrollment), with the
 * student's own fields under `student`. Kept in step by hand, like the rest of
 * the API contract.
 */
export type StudentRow = {
  /** The enrollment's id: what the list, edit and delete address. */
  id: number;
  student: Student;
  academicYear: { id: number; name: string };
  schoolClass: {
    id: number;
    name: string;
    classGroup: { id: number; name: string };
  } | null;
  trainingSystem: { id: number; name: string } | null;
  transferInDate: string | null;
  note: string | null;
  createdAt: string;
  updatedAt: string | null;
};

export type Gender = "MALE" | "FEMALE" | "OTHER";

export type Student = {
  id: number;
  studentCode: string;
  fullName: string;
  dateOfBirth: string | null;
  gender: Gender | null;
  personalId: string | null;
  passportNumber: string | null;
  statisticCode: string | null;
  admissionYear: number | null;
  healthInsuranceCode: string | null;
  province: string | null;
  district: string | null;
  ward: string | null;
  area: string | null;
  houseNumber: string | null;
  hamlet: string | null;
  address: string | null;
  guardianName: string | null;
  guardianPhone: string | null;
  guardianEmail: string | null;
  bankName: string | null;
  bankAccountHolder: string | null;
  bankAccountNumber: string | null;
  needsInvoice: boolean;
};

export const GENDER_LABELS: Record<Gender, string> = {
  MALE: "Nam",
  FEMALE: "Nữ",
  OTHER: "Khác",
};

/** `GET /students/structure`: the year's khối lớp and lớp with student counts. */
export type StudentStructure = {
  total: number;
  unassigned: number;
  groups: StructureGroup[];
};

export type StructureGroup = {
  id: number;
  name: string;
  studentCount: number;
  classes: StructureClass[];
};

export type StructureClass = {
  id: number;
  name: string;
  studentCount: number;
};

/** Which part of the tree is selected: everything, the unplaced, a khối or a lớp. */
export type StructureNode =
  | { type: "all" }
  | { type: "unassigned" }
  | { type: "group"; id: number }
  | { type: "class"; id: number };

/** The list filters a node stands for. */
export function nodeFilters(
  node: StructureNode
): { field: string; operator: "eq"; value: string }[] {
  switch (node.type) {
    case "unassigned":
      return [{ field: "unassigned", operator: "eq", value: "true" }];
    case "group":
      return [{ field: "classGroup.id", operator: "eq", value: String(node.id) }];
    case "class":
      return [{ field: "schoolClass.id", operator: "eq", value: String(node.id) }];
    default:
      return [];
  }
}

/** What the add and edit dialog holds. Everything is text until it is sent. */
export type StudentFormValues = {
  fullName: string;
  studentCode: string;
  dateOfBirth: string;
  gender: Gender | "";
  schoolClassId: string;
  trainingSystemId: string;
  personalId: string;
  passportNumber: string;
  statisticCode: string;
  admissionYear: string;
  transferInDate: string;
  healthInsuranceCode: string;
  province: string;
  district: string;
  ward: string;
  area: string;
  houseNumber: string;
  hamlet: string;
  address: string;
  guardianName: string;
  guardianPhone: string;
  guardianEmail: string;
  bankName: string;
  bankAccountHolder: string;
  bankAccountNumber: string;
  needsInvoice: boolean;
};
