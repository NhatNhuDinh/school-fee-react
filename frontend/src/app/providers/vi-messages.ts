import type { I18nProvider } from "@refinedev/core";

/**
 * This is NOT a multi-language system. The application is Vietnamese only.
 *
 * Refine builds some strings itself (toasts such as "Successfully created
 * academic_year", button labels, breadcrumb action names) and the only way to
 * make it say them in Vietnamese is to hand it an `i18nProvider`. This file is
 * the static table behind that provider: no locale switching, no extra library.
 */

/** Vietnamese names of the resources, as they appear inside a sentence. */
const RESOURCE_NAMES: Record<string, string> = {
  academic_years: "năm học",
  academic_year: "năm học",
  priority_categories: "diện ưu tiên",
  priority_category: "diện ưu tiên",
  students: "học sinh",
  student: "học sinh",
  class_groups: "khối lớp",
  class_group: "khối lớp",
  school_classes: "lớp",
  school_class: "lớp",
  training_systems: "hệ đào tạo",
  training_system: "hệ đào tạo",
  measurement_units: "đơn vị tính",
  measurement_unit: "đơn vị tính",
  users: "người dùng",
  user: "người dùng",
  organizations: "đơn vị",
  organization: "đơn vị",
  roles: "vai trò",
  role: "vai trò",
  permissions: "mã quyền",
  permission: "quyền chức năng",
  row_level_policies: "quyền dữ liệu",
  row_level_policy: "quyền dữ liệu",
  notifications: "thông báo",
  notification: "thông báo",
};

const MESSAGES: Record<string, string> = {
  "notifications.success": "Thành công",
  "notifications.error": "Lỗi (mã trạng thái: {{statusCode}})",
  "notifications.createSuccess": "Đã thêm mới {{resource}}",
  "notifications.createError":
    "Lỗi khi thêm mới {{resource}} (mã trạng thái: {{statusCode}})",
  "notifications.editSuccess": "Đã cập nhật {{resource}}",
  "notifications.editError":
    "Lỗi khi cập nhật {{resource}} (mã trạng thái: {{statusCode}})",
  "notifications.deleteSuccess": "Đã xóa {{resource}}",
  "notifications.deleteError":
    "Lỗi khi xóa {{resource}} (mã trạng thái: {{statusCode}})",
  "notifications.undoable": "Bạn có {{seconds}} giây để hoàn tác",

  "buttons.create": "Thêm mới",
  "buttons.save": "Lưu",
  "buttons.edit": "Sửa",
  "buttons.list": "Danh sách",
  "buttons.show": "Xem",
  "buttons.delete": "Xóa",
  "buttons.cancel": "Hủy",
  "buttons.confirm": "Bạn có chắc chắn?",
  "buttons.refresh": "Làm mới",
  "buttons.clone": "Nhân bản",
  "buttons.import": "Nhập",
  "buttons.export": "Xuất",
  "buttons.undo": "Hoàn tác",
  "buttons.logout": "Đăng xuất",
  "buttons.notAccessTitle": "Bạn không có quyền thực hiện thao tác này",

  "actions.list": "Danh sách",
  "actions.create": "Thêm mới",
  "actions.edit": "Sửa",
  "actions.show": "Xem",
  "actions.clone": "Nhân bản",
};

function interpolate(
  template: string,
  options?: Record<string, unknown>,
): string {
  return template.replace(/\{\{(\w+)\}\}/g, (_, name: string) => {
    const value = options?.[name];
    if (value === undefined || value === null) return "";
    const text = String(value);
    return name === "resource" ? (RESOURCE_NAMES[text] ?? text) : text;
  });
}

export const viI18nProvider: I18nProvider = {
  translate: (key, options, defaultMessage) => {
    // `translate(key, "fallback")` is a supported call shape.
    const fallback =
      defaultMessage ?? (typeof options === "string" ? options : undefined);
    const opts =
      typeof options === "object" && options !== null
        ? (options as Record<string, unknown>)
        : undefined;

    // `{name}.{name}` is how Refine asks for a resource's own name.
    const [head, tail, ...rest] = key.split(".");
    if (rest.length === 0 && head === tail && RESOURCE_NAMES[head]) {
      return RESOURCE_NAMES[head];
    }

    const message = MESSAGES[key];
    if (message !== undefined) return interpolate(message, opts);
    return fallback ?? key;
  },
  changeLocale: () => Promise.resolve(),
  getLocale: () => "vi",
};
