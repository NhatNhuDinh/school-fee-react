import {
  CatalogCreate,
  CatalogEdit,
  type CatalogFormProps,
} from "./catalog-form";
import { CatalogList } from "./catalog-list";

/**
 * The two catalogues a school shares across its years. Each is a name under a
 * school, so they differ only in the words on the screen.
 */

const TRAINING_SYSTEM: CatalogFormProps = {
  nameLabel: "Tên hệ đào tạo",
  placeholder: "Chính quy",
};

const MEASUREMENT_UNIT: CatalogFormProps = {
  nameLabel: "Tên đơn vị tính",
  placeholder: "Tháng",
};

export const TrainingSystemList = () => (
  <CatalogList
    nameLabel={TRAINING_SYSTEM.nameLabel}
  />
);
export const TrainingSystemCreate = () => <CatalogCreate {...TRAINING_SYSTEM} />;
export const TrainingSystemEdit = () => <CatalogEdit {...TRAINING_SYSTEM} />;

export const MeasurementUnitList = () => (
  <CatalogList
    nameLabel={MEASUREMENT_UNIT.nameLabel}
  />
);
export const MeasurementUnitCreate = () => <CatalogCreate {...MEASUREMENT_UNIT} />;
export const MeasurementUnitEdit = () => <CatalogEdit {...MEASUREMENT_UNIT} />;
