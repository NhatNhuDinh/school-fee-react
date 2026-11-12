/**
 * A row of one of the catalogues a school shares across its years — a training
 * system or a measurement unit. Both are `{ name }` under a school, so they
 * share one shape: `TrainingSystemResponse` and `MeasurementUnitResponse` on the
 * backend. Kept in step by hand, like the rest of the API contract.
 */
export type SchoolCatalogItem = {
  id: number;
  organization: SchoolCatalogSchool;
  name: string;
  createdAt: string;
  updatedAt: string | null;
};

/** The school as a catalogue row reports it. A school is an organization. */
export type SchoolCatalogSchool = {
  id: number;
  code: string;
  name: string;
};

/**
 * What the create form holds. `organization.id` is an empty string until
 * somebody picks a school; the request leaves the field out in that case and the
 * API uses the caller's own school.
 */
export type SchoolCatalogFormValues = {
  organization: { id: string };
  name: string;
};
