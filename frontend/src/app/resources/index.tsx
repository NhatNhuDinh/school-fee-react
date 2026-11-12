import type { ResourceProps } from "@refinedev/core";
import {
  Banknote,
  Bell,
  School,
  CalendarDays,
  FileText,
  GraduationCap,
  LayoutDashboard,
  KeyRound,
  List,
  Scale,
  ShieldCheck,
  ShieldHalf,
  SquareCheck,
  Star,
  Users,
} from "lucide-react";

/**
 * Resource definitions passed to `<Refine resources={...} />`.
 *
 * A resource maps a name to its action routes. Refine infers the current
 * resource from the URL, which is what lets hooks like `useTable`, `useForm`
 * and the `*Button` components work without being told which resource they are
 * operating on.
 *
 * The resource name is load-bearing twice over. It is the path segment the data
 * provider builds every request from (`/api/{name}`), and it is the first half
 * of the permission code the access control provider asks about
 * (`{name}:read`). Renaming one here means renaming the endpoint and the seeded
 * permission with it.
 *
 * `meta` keys used here:
 *   - `label`     display name in the sidebar, breadcrumb and document title
 *   - `icon`      sidebar and breadcrumb icon (falls back to a generic list icon)
 *   - `parent`    nests this resource under another one (Refine)
 *   - `group`     renders the resource as a sidebar section header
 *                 (convention of `shared/components/layout/sidebar.tsx`,
 *                 not a Refine API)
 *   - `canDelete` declared, but **read by nothing**. It is a Refine resource
 *                 type with no runtime consumer in this UI layer: the list
 *                 action columns render a `<DeleteButton>` outright and so does
 *                 `ShowViewHeader`, both leaving the decision to access control
 *                 (`{resource}:delete`). The values below are therefore inert —
 *                 `categories` says `false` and still offers a delete, because
 *                 `categories:delete` is a seeded code. Do not start honouring
 *                 it in one view only; that would give a resource a delete on
 *                 its table and none on its detail page.
 *
 * This is one half of a declaration; the other is the `<Route>` tree in
 * `app/router/routes.tsx`. Adding a resource means touching both, and if the
 * action paths here drift from the route paths there, buttons and breadcrumbs
 * silently navigate nowhere.
 */
export const resources: ResourceProps[] = [
  // The landing screen, and the one resource here with nothing behind it: no
  // endpoint, no rows, no permission. It is declared so `useMenu` has an item
  // to render and so the breadcrumb and document title have a label; the code
  // that would otherwise be derived from this name (`dashboard:read`) is
  // suppressed in `shared/lib/permissions.ts`, or the sidebar would hide the
  // item from everybody.
  {
    name: "dashboard",
    list: "/dashboard",
    meta: {
      label: "Trang chủ",
      icon: <LayoutDashboard className="h-4 w-4" />,
    },
  },

  // Fee collection. Laid out as in the application this replaces; the screens
  // come in a later phase, so each entry opens a notice for now
  // (see shared/lib/placeholders.ts).
  {
    name: "fee_management",
    meta: {
      label: "Thu phí",
      group: true,
    },
  },
  {
    name: "fee_items",
    list: "/fee_items",
    meta: {
      label: "Khoản thu",
      icon: <FileText className="h-4 w-4" />,
      parent: "fee_management",
    },
  },
  {
    name: "fee_registrations",
    list: "/fee_registrations",
    meta: {
      label: "Đăng ký thu",
      icon: <SquareCheck className="h-4 w-4" />,
      parent: "fee_management",
    },
  },
  {
    name: "fee_collection_batches",
    list: "/fee_collection_batches",
    show: "/fee_collection_batches/show/:id",
    meta: {
      label: "Đợt thu",
      icon: <CalendarDays className="h-4 w-4" />,
      parent: "fee_management",
    },
  },
  {
    // Reached through a đợt thu's own page, never from the menu: it is a resource so that the
    // data provider, the permission codes (`fee_notifications:*`) and the row scope all work.
    name: "fee_notifications",
    meta: {
      label: "Thông báo thu tiền",
      hide: true,
    },
  },
  {
    name: "fee_collection_payments",
    list: "/fee_collection_payments",
    meta: {
      label: "Thu tiền",
      icon: <Banknote className="h-4 w-4" />,
      parent: "fee_management",
    },
  },

  // The school's own catalogues. Every resource below this heading belongs to
  // one school and is isolated from the others by the tenant filter on the API;
  // the screens do nothing special for that. Two sub-menus, as before: what
  // belongs to the school year, and what is shared across them.
  {
    name: "catalogs",
    meta: {
      label: "Danh mục",
      group: true,
    },
  },
  {
    name: "academic_catalogs",
    meta: {
      label: "Học vụ",
      icon: <CalendarDays className="h-4 w-4" />,
      parent: "catalogs",
    },
  },
  {
    name: "class_groups",
    list: "/class_groups",
    create: "/class_groups/create",
    edit: "/class_groups/edit/:id",
    meta: {
      label: "Khối lớp",
      canDelete: true,
      icon: <Users className="h-4 w-4" />,
      parent: "academic_catalogs",
    },
  },
  {
    // The lớp inside a khối lớp. It has no screen of its own, only the panel on
    // the khối lớp edit page, so it is declared for the data hooks and hidden.
    name: "school_classes",
    meta: {
      label: "Lớp",
      hide: true,
      parent: "academic_catalogs",
    },
  },
  {
    name: "academic_years",
    list: "/academic_years",
    create: "/academic_years/create",
    edit: "/academic_years/edit/:id",
    show: "/academic_years/show/:id",
    meta: {
      label: "Năm học",
      canDelete: true,
      icon: <CalendarDays className="h-4 w-4" />,
      parent: "academic_catalogs",
    },
  },
  {
    name: "students",
    list: "/students",
    meta: {
      label: "Học sinh",
      canDelete: true,
      icon: <Users className="h-4 w-4" />,
      parent: "academic_catalogs",
    },
  },
  {
    name: "priority_categories",
    list: "/priority_categories",
    create: "/priority_categories/create",
    edit: "/priority_categories/edit/:id",
    meta: {
      label: "Ưu tiên",
      canDelete: true,
      icon: <Star className="h-4 w-4" />,
      parent: "academic_catalogs",
    },
  },
  {
    name: "common_catalogs",
    meta: {
      label: "Dùng chung",
      icon: <List className="h-4 w-4" />,
      parent: "catalogs",
    },
  },
  {
    name: "training_systems",
    list: "/training_systems",
    create: "/training_systems/create",
    edit: "/training_systems/edit/:id",
    meta: {
      label: "Hệ đào tạo",
      canDelete: true,
      icon: <GraduationCap className="h-4 w-4" />,
      parent: "common_catalogs",
    },
  },
  {
    name: "measurement_units",
    list: "/measurement_units",
    create: "/measurement_units/create",
    edit: "/measurement_units/edit/:id",
    meta: {
      label: "Đơn vị tính",
      canDelete: true,
      icon: <Scale className="h-4 w-4" />,
      parent: "common_catalogs",
    },
  },

  // Administration. The whole section disappears for anyone without the
  // matching permissions -- but not by itself: `useMenu` does not consult
  // access control, so `sidebar.tsx` filters the tree it returns. See
  // `useAccessibleMenuItems` there.
  {
    name: "administration",
    meta: {
      label: "Quản trị",
      group: true,
    },
  },
  {
    name: "users",
    list: "/users",
    create: "/users/create",
    edit: "/users/edit/:id",
    show: "/users/show/:id",
    meta: {
      label: "Người dùng",
      canDelete: true,
      icon: <Users className="h-4 w-4" />,
      parent: "administration",
    },
  },
  // The schools. A school is an organization on the API, which is why the
  // resource keeps that name -- it is the path segment and the first half of
  // the permission codes -- while the screens call it a school. This is where a
  // system administrator creates a school; an account is attached to one on the
  // Users screen, and from then on sees only that school's data.
  {
    name: "organizations",
    list: "/organizations",
    create: "/organizations/create",
    edit: "/organizations/edit/:id",
    show: "/organizations/show/:id",
    meta: {
      label: "Đơn vị",
      canDelete: true,
      icon: <School className="h-4 w-4" />,
      parent: "administration",
    },
  },
  {
    name: "roles",
    list: "/roles",
    create: "/roles/create",
    edit: "/roles/edit/:id",
    show: "/roles/show/:id",
    meta: {
      // The old application called this screen "Quyền chức năng": a list of roles,
      // each with the functions it may use. The name moves here with it.
      label: "Quyền chức năng",
      canDelete: true,
      icon: <ShieldCheck className="h-4 w-4" />,
      parent: "administration",
    },
  },
  // Read-only: a list and a show, and no create, edit or delete action. The
  // absent actions are what stops the screen promising something the API will
  // not do — a permission only means something once an endpoint checks for it,
  // so the catalogue is written by migrations. Refine derives no route it was
  // not given, and `permissions:write` / `permissions:delete` are not codes any
  // migration seeds, so the buttons would be hidden even if they were.
  {
    name: "permissions",
    list: "/permissions",
    show: "/permissions/show/:id",
    meta: {
      label: "Mã quyền",
      canDelete: false,
      icon: <KeyRound className="h-4 w-4" />,
      parent: "administration",
      // A read-only catalogue of the permission codes. The old application had no
      // such screen, so it is not offered in the menu; the role screens still link
      // to it ("which roles grant this"), so the routes stay.
      hide: true,
    },
  },
  // Data scopes: which rows each role may reach, as opposed to which resources
  // it may open at all. Only ADMIN holds `row_level_policies:read`, so
  // `useAccessibleMenuItems` in the sidebar hides this from everybody else --
  // and hides the whole Administration group with it if nothing else survives.
  //
  // Plain full-page routes rather than dialogs, for the same reason `roles` and
  // `users` are: the condition builder needs vertical room a dialog over a list
  // does not have.
  {
    name: "row_level_policies",
    list: "/row-level-policies",
    create: "/row-level-policies/create",
    edit: "/row-level-policies/edit/:id",
    show: "/row-level-policies/show/:id",
    meta: {
      label: "Quyền dữ liệu",
      canDelete: true,
      icon: <ShieldHalf className="h-4 w-4" />,
      parent: "administration",
    },
  },
  {
    name: "notifications",
    list: "/notifications",
    meta: {
      label: "Thông báo",
      canDelete: true,
      icon: <Bell className="h-4 w-4" />,
    },
  },
];
