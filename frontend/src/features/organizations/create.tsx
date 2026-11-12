import { useForm } from "@refinedev/react-hook-form";
import { useNavigate } from "react-router";

import {
  CreateView,
  CreateViewHeader,
} from "@/shared/components/views/create-view";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Textarea } from "@/shared/ui/textarea";
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
import { STATUS_OPTIONS, type OrganizationStatus } from "@/domains/organization/types";
import {
  FormActions,
  FormPanel,
} from "@/shared/components/form/form-panel";
import { LogoField } from "@/shared/components/form/logo-field";

/**
 * The create payload, which is `OrganizationRequest` on the backend rather than
 * the `OrganizationResponse` shape the rest of the resource speaks. Naming it
 * keeps the defaults below and the submit handler agreeing on one type.
 */
type OrganizationFormValues = {
  code: string;
  name: string;
  description: string;
  email: string;
  phone: string;
  address: string;
  logo: string;
  status: OrganizationStatus;
};

/**
 * Annotated rather than inferred, so `status` widens to the union instead of
 * being pinned to the literal "active" the object happens to start with.
 */
const DEFAULT_VALUES: OrganizationFormValues = {
  code: "",
  name: "",
  description: "",
  email: "",
  phone: "",
  address: "",
  logo: "",
  status: "active",
};

export const OrganizationCreate = () => {
  const navigate = useNavigate();

  const {
    refineCore: { onFinish },
    ...form
  } = useForm({
    refineCoreProps: {},
    defaultValues: DEFAULT_VALUES,
  });

  function onSubmit(values: OrganizationFormValues) {
    onFinish(values);
  }

  return (
    <CreateView>
      <CreateViewHeader />
      <Form {...form}>
        <form
          onSubmit={form.handleSubmit(onSubmit)}
        >
          <FormPanel contentClassName="grid gap-6 space-y-0 lg:grid-cols-2">
            <FormField
              control={form.control}
              name="logo"
              render={({ field }) => (
                <FormItem className="lg:col-span-2">
                  <FormLabel>Logo trường</FormLabel>
                  <FormControl>
                    <LogoField value={field.value} onChange={field.onChange} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="code"
              rules={{
                required: "Vui lòng nhập mã",
                maxLength: { value: 64, message: "Tối đa 64 ký tự" },
                // Mirrors the pattern the API enforces, so the message arrives
                // before the round trip rather than after it.
                pattern: {
                  value: /^[A-Za-z0-9._-]+$/,
                  message: "Chỉ gồm chữ cái, chữ số, dấu chấm, gạch dưới và gạch ngang",
                },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Mã</FormLabel>
                  <FormControl>
                    <Input {...field} value={field.value ?? ""} placeholder="NBK" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="name"
              rules={{
                required: "Vui lòng nhập tên",
                maxLength: { value: 255, message: "Tối đa 255 ký tự" },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Tên</FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      value={field.value ?? ""}
                      placeholder="THPT Nguyễn Bỉnh Khiêm"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              rules={{
                maxLength: { value: 500, message: "Tối đa 500 ký tự" },
              }}
              render={({ field }) => (
                <FormItem className="lg:col-span-2">
                  <FormLabel>Mô tả</FormLabel>
                  <FormControl>
                    <Textarea
                      {...field}
                      value={field.value ?? ""}
                      placeholder="Giới thiệu về trường"
                      rows={3}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="contents">
              <FormField
                control={form.control}
                name="email"
                rules={{
                  pattern: {
                    value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                    message: "Email không hợp lệ",
                  },
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        type="email"
                        value={field.value ?? ""}
                        placeholder="lienhe@nbk.edu.vn"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="phone"
                rules={{
                  maxLength: { value: 64, message: "Tối đa 64 ký tự" },
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Điện thoại</FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        value={field.value ?? ""}
                        placeholder="024 3900 0000"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="address"
              rules={{
                maxLength: { value: 500, message: "Tối đa 500 ký tự" },
              }}
              render={({ field }) => (
                <FormItem className="lg:col-span-2">
                  <FormLabel>Địa chỉ</FormLabel>
                  <FormControl>
                    <Textarea
                      {...field}
                      value={field.value ?? ""}
                      placeholder="Số nhà, đường, quận/huyện, tỉnh/thành phố"
                      rows={2}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="status"
              render={({ field }) => (
                <FormItem>
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

            <FormActions className="lg:col-span-2">
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? "Đang tạo…" : "Thêm mới"}
              </Button>
              <Button type="button" variant="outline" onClick={() => navigate(-1)}>
                Hủy
              </Button>
            </FormActions>
          </FormPanel>
        </form>
      </Form>
    </CreateView>
  );
};
