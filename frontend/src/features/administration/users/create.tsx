import { useForm } from "@refinedev/react-hook-form";
import { useNavigate } from "react-router";

import {
  CreateView,
  CreateViewHeader,
} from "@/shared/components/views/create-view";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { InputPassword } from "@/shared/components/form/input-password";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/shared/ui/form";
import {
  FormSection,
  FormSectionActions,
  SectionedFormPanel,
} from "@/shared/components/form/form-section";
import { RolePicker, type RoleRef } from "./role-picker";
import { PlacementPicker, type PlacementRef } from "./placement-picker";
import { STATUS_OPTIONS, type UserStatus } from "@/domains/user/types";

/**
 * The create payload, which is `UserRequest` on the backend rather than the
 * `UserResponse` shape the rest of the resource speaks. Naming it keeps the
 * defaults below and the submit handler agreeing on one type.
 */
type UserFormValues = {
  username: string;
  email: string;
  fullName: string;
  password: string;
  status: UserStatus;
  roles: RoleRef[];
  organization: PlacementRef;
};

/**
 * Annotated rather than inferred, so `status` widens to the union instead of
 * being pinned to the literal "active" the object happens to start with.
 */
const DEFAULT_VALUES: UserFormValues = {
  username: "",
  email: "",
  fullName: "",
  password: "",
  status: "active",
  roles: [],
  // `{ id: null }` rather than omitted: it is the shape the API reads as "no
  // placement", and it keeps the picker's value a stable object either way.
  organization: { id: null },
};

/**
 * Creating an account.
 *
 * This is the only way a user comes into existence — there is no
 * self-registration — so the password is set here by whoever creates the
 * account and handed over out of band.
 *
 * The same three zones as the edit screen, in the same order and the same
 * column spans, with sign-in credentials as a fourth: making an account and
 * changing one should not be two different shapes, and the one real difference
 * between them — a password that is set once and never shown again — is the
 * thing that gets its own heading.
 */
export const UserCreate = () => {
  const navigate = useNavigate();

  const {
    refineCore: { onFinish },
    ...form
  } = useForm({
    refineCoreProps: {},
    defaultValues: DEFAULT_VALUES,
  });

  function onSubmit(values: UserFormValues) {
    onFinish(values);
  }

  return (
    <CreateView>
      <CreateViewHeader />
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="min-w-0">
          <SectionedFormPanel>
            <FormSection
              title="Thông tin tài khoản"
            >
              <FormField
                control={form.control}
                name="username"
                rules={{
                  required: "Vui lòng nhập tên đăng nhập",
                  minLength: { value: 3, message: "Tối thiểu 3 ký tự" },
                  // Mirrors the pattern the API enforces, so the message
                  // arrives before the round trip rather than after it.
                  pattern: {
                    value: /^[a-zA-Z0-9._-]+$/,
                    message:
                      "Chỉ gồm chữ cái, chữ số, dấu chấm, gạch dưới và gạch ngang",
                  },
                }}
                render={({ field }) => (
                  <FormItem className="md:col-span-4">
                    <FormLabel>Tên đăng nhập</FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        value={field.value ?? ""}
                        placeholder="nguyenvana"
                        className="font-mono"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="email"
                rules={{
                  required: "Vui lòng nhập email",
                  pattern: {
                    value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                    message: "Email không hợp lệ",
                  },
                }}
                render={({ field }) => (
                  <FormItem className="md:col-span-4">
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        type="email"
                        value={field.value ?? ""}
                        placeholder="nguyenvana@nbk.edu.vn"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="fullName"
                rules={{ required: "Vui lòng nhập họ và tên" }}
                render={({ field }) => (
                  <FormItem className="md:col-span-4">
                    <FormLabel>Họ và tên</FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        value={field.value ?? ""}
                        placeholder="Nguyễn Văn A"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </FormSection>

            <FormSection
              title="Đăng nhập"
            >
              <FormField
                control={form.control}
                name="password"
                rules={{
                  required: "Vui lòng nhập mật khẩu",
                  minLength: { value: 8, message: "Tối thiểu 8 ký tự" },
                }}
                render={({ field }) => (
                  <FormItem className="md:col-span-4">
                    <FormLabel>Mật khẩu ban đầu</FormLabel>
                    <FormControl>
                      <InputPassword
                        {...field}
                        value={field.value ?? ""}
                        autoComplete="new-password"
                        placeholder="Tối thiểu 8 ký tự"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </FormSection>

            <FormSection
              title="Trường và trạng thái"
            >
              <FormField
                control={form.control}
                name="status"
                render={({ field }) => (
                  <FormItem className="md:col-span-4">
                    <FormLabel>Trạng thái</FormLabel>
                    <Select
                      onValueChange={field.onChange}
                      value={field.value ?? "active"}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder="Chọn trạng thái" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {STATUS_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* The school the account works for; `contents` puts the field on this
                  section's own grid rather than in a column of its own. */}
              <PlacementPicker
                className="contents"
                itemClassName="md:col-span-4"
                organization={form.watch("organization")}
                onOrganizationChange={(value) =>
                  form.setValue("organization", value)
                }
              />
            </FormSection>

            <FormSection
              title="Vai trò"
            >
              <FormField
                control={form.control}
                name="roles"
                rules={{
                  validate: (value) =>
                    (Array.isArray(value) && value.length > 0) ||
                    "Chọn ít nhất một vai trò",
                }}
                render={({ field }) => (
                  <FormItem className="md:col-span-12">
                    <FormControl>
                      <RolePicker
                        value={field.value}
                        onChange={field.onChange}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </FormSection>

            <FormSectionActions>
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? "Đang tạo…" : "Thêm mới"}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => navigate(-1)}
              >
                Hủy
              </Button>
            </FormSectionActions>
          </SectionedFormPanel>
        </form>
      </Form>
    </CreateView>
  );
};
