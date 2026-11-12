import { Authenticated, CanAccess } from "@refinedev/core";
import { Navigate, Outlet, Route, Routes } from "react-router";

import { Dashboard } from "@/features/dashboard";
import {
  OrganizationList,
  OrganizationCreate,
  OrganizationEdit,
  OrganizationShow,
} from "@/features/organizations";
import {
  AcademicYearEntry,
  AcademicYearCreate,
  AcademicYearEdit,
  AcademicYearShow,
} from "@/features/academic-years";
import {
  TrainingSystemList,
  TrainingSystemCreate,
  TrainingSystemEdit,
  MeasurementUnitList,
  MeasurementUnitCreate,
  MeasurementUnitEdit,
} from "@/features/school-catalogs";
import {
  ClassGroupCreate,
  ClassGroupEdit,
  ClassGroupList,
} from "@/features/class-groups";
import {
  PriorityCategoryCreate,
  PriorityCategoryEdit,
  PriorityCategoryList,
} from "@/features/priority-categories";
import {
  FeeCollectionBatchList,
  FeeCollectionBatchShow,
} from "@/features/fee-collection-batches";
import { FeeItemList } from "@/features/fee-items";
import { FeeRegistrationPage } from "@/features/fee-registrations";
import { StudentList } from "@/features/students";
import { NotificationList } from "@/features/notifications";
import {
  UserList,
  UserCreate,
  UserEdit,
  UserShow,
  RoleList,
  RoleCreate,
  RoleEdit,
  RoleShow,
  PermissionList,
  PermissionShow,
  RowLevelPolicyList,
  RowLevelPolicyCreate,
  RowLevelPolicyEdit,
  RowLevelPolicyShow,
} from "@/features/administration";
import { Login, Profile } from "@/features/identity";
import { ErrorComponent } from "@/shared/components/layout/error-component";
import { Forbidden } from "@/shared/components/layout/forbidden";
import { Layout } from "@/shared/components/layout/layout";
import { ComingSoon } from "@/shared/components/views/coming-soon";
import { PLACEHOLDER_RESOURCES } from "@/shared/lib/placeholders";

/**
 * Route tree rendered inside `<Refine>`. Every path here must line up with the
 * action routes declared in `app/resources/index.tsx`.
 *
 * The tree has two halves. Everything under `<Authenticated>` requires a
 * session and renders inside the shell; the login screen sits outside it and
 * renders bare. `<Authenticated>` calls the auth provider's `check`, which is
 * what turns an expired access token into a silent refresh rather than a
 * bounce to the login form.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route
        element={
          <Authenticated key="authenticated-routes" redirectOnFail="/login">
            <Layout>
              <Outlet />
            </Layout>
          </Authenticated>
        }
      >
        {/*
          The application opens on the dashboard rather than on a resource's
          table. `Navigate` rather than `NavigateToResource`, because the
          dashboard is a screen and not a resource: there is no `list` action to
          resolve a path from beyond the one declared above.
        */}
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<Dashboard />} />
        {/*
          Schools (organizations on the API) — plain full-page routes, and
          guarded like the administration ones below: the sidebar hides them,
          but a typed or bookmarked URL would otherwise render a screen whose
          list request 403s.
        */}
        <Route
          path="/organizations"
          element={
            <CanAccess
              resource="organizations"
              action="list"
              fallback={<Forbidden resource="organizations" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<OrganizationList />} />
          <Route path="create" element={<OrganizationCreate />} />
          <Route path="edit/:id" element={<OrganizationEdit />} />
          <Route path="show/:id" element={<OrganizationShow />} />
        </Route>
        {/* Menu entries whose screens are a later phase. */}
        {PLACEHOLDER_RESOURCES.map((name) => (
          <Route key={name} path={`/${name}`} element={<ComingSoon />} />
        ))}

        <Route
          path="/academic_years"
          element={
            <CanAccess
              resource="academic_years"
              action="list"
              fallback={<Forbidden resource="academic_years" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<AcademicYearEntry />} />
          <Route path="create" element={<AcademicYearCreate />} />
          <Route path="edit/:id" element={<AcademicYearEdit />} />
          <Route path="show/:id" element={<AcademicYearShow />} />
        </Route>

        <Route
          path="/class_groups"
          element={
            <CanAccess
              resource="class_groups"
              action="list"
              fallback={<Forbidden resource="class_groups" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<ClassGroupList />} />
          <Route path="create" element={<ClassGroupCreate />} />
          <Route path="edit/:id" element={<ClassGroupEdit />} />
        </Route>

        <Route
          path="/priority_categories"
          element={
            <CanAccess
              resource="priority_categories"
              action="list"
              fallback={<Forbidden resource="priority_categories" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<PriorityCategoryList />} />
          <Route path="create" element={<PriorityCategoryCreate />} />
          <Route path="edit/:id" element={<PriorityCategoryEdit />} />
        </Route>
        <Route
          path="/students"
          element={
            <CanAccess
              resource="students"
              action="list"
              fallback={<Forbidden resource="students" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<StudentList />} />
        </Route>
        <Route
          path="/fee_items"
          element={
            <CanAccess
              resource="fee_items"
              action="list"
              fallback={<Forbidden resource="fee_items" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<FeeItemList />} />
        </Route>
        <Route
          path="/fee_registrations"
          element={
            <CanAccess
              resource="fee_registrations"
              action="list"
              fallback={<Forbidden resource="fee_registrations" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<FeeRegistrationPage />} />
        </Route>
        <Route
          path="/fee_collection_batches"
          element={
            <CanAccess
              resource="fee_collection_batches"
              action="list"
              fallback={<Forbidden resource="fee_collection_batches" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<FeeCollectionBatchList />} />
          <Route path="show/:id" element={<FeeCollectionBatchShow />} />
        </Route>
        <Route
          path="/training_systems"
          element={
            <CanAccess
              resource="training_systems"
              action="list"
              fallback={<Forbidden resource="training_systems" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<TrainingSystemList />} />
          <Route path="create" element={<TrainingSystemCreate />} />
          <Route path="edit/:id" element={<TrainingSystemEdit />} />
        </Route>
        <Route
          path="/measurement_units"
          element={
            <CanAccess
              resource="measurement_units"
              action="list"
              fallback={<Forbidden resource="measurement_units" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<MeasurementUnitList />} />
          <Route path="create" element={<MeasurementUnitCreate />} />
          <Route path="edit/:id" element={<MeasurementUnitEdit />} />
        </Route>

        {/*
          Administration — plain full-page routes, each wrapped
          in `<CanAccess>`. The sidebar already hides these, so the guard only
          matters for a typed or bookmarked URL: without it the screen would
          render and the list request would 403, leaving an empty table and a
          toast instead of an explanation.

          This is a courtesy, not the boundary. The API checks the same
          permission on every request regardless of what renders here.
        */}
        <Route
          path="/users"
          element={
            <CanAccess
              resource="users"
              action="list"
              fallback={<Forbidden resource="users" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<UserList />} />
          <Route path="create" element={<UserCreate />} />
          <Route path="edit/:id" element={<UserEdit />} />
          <Route path="show/:id" element={<UserShow />} />
        </Route>
        <Route
          path="/roles"
          element={
            <CanAccess
              resource="roles"
              action="list"
              fallback={<Forbidden resource="roles" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<RoleList />} />
          <Route path="create" element={<RoleCreate />} />
          <Route path="edit/:id" element={<RoleEdit />} />
          <Route path="show/:id" element={<RoleShow />} />
        </Route>
        <Route
          path="/row-level-policies"
          element={
            <CanAccess
              resource="row_level_policies"
              action="list"
              fallback={<Forbidden resource="row_level_policies" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<RowLevelPolicyList />} />
          <Route path="create" element={<RowLevelPolicyCreate />} />
          <Route path="edit/:id" element={<RowLevelPolicyEdit />} />
          <Route path="show/:id" element={<RowLevelPolicyShow />} />
        </Route>
        <Route
          path="/permissions"
          element={
            <CanAccess
              resource="permissions"
              action="list"
              fallback={<Forbidden resource="permissions" />}
            >
              <Outlet />
            </CanAccess>
          }
        >
          <Route index element={<PermissionList />} />
          <Route path="show/:id" element={<PermissionShow />} />
        </Route>

        {/*
          Notifications have a list and nothing else. No create screen, because
          the application raises a notification rather than a person writing
          one; no edit screen, because the only change a recipient may make is
          marking one read. No <CanAccess> wrapper either: every system role is
          granted notifications:read, and the rows are the caller's own, so
          there is no URL to guard against.
        */}
        <Route path="/notifications" element={<NotificationList />} />

        {/* Not a resource: no list of "my accounts", no id in the URL. */}
        <Route path="/profile" element={<Profile />} />

        <Route path="*" element={<ErrorComponent />} />
      </Route>

      {/*
        The unauthenticated half. `fallback` is what renders when there is no
        session; a signed-in visitor is sent to the dashboard instead, so the
        login URL never shows a form to someone who is already past it.
      */}
      <Route
        element={
          <Authenticated key="unauthenticated-routes" fallback={<Outlet />}>
            <Navigate to="/" replace />
          </Authenticated>
        }
      >
        <Route path="/login" element={<Login />} />
      </Route>
    </Routes>
  );
}
