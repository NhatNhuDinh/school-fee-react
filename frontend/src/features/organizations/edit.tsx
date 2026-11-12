import { useForm } from "@refinedev/react-hook-form";
import { useNavigate } from "react-router";

import {
  EditView,
  EditViewHeader,
} from "@/shared/components/views/edit-view";
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
import { STATUS_OPTIONS } from "@/domains/organization/types";
import {
  FormActions,
  FormPanel,
} from "@/shared/components/form/form-panel";
import { LogoField } from "@/shared/components/form/logo-field";

export const OrganizationEdit = () => {
  const navigate = useNavigate();

  const {
    refineCore: { onFinish },
    ...form
  } = useForm({
    refineCoreProps: {},
  });

  function onSubmit(values: Record<string, unknown>) {
    onFinish(values);
  }

  return (
    <EditView>
      <EditViewHeader />
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
                pattern: {
                  value: /^[A-Za-z0-9._-]+$/,
                  message: "Chỉ gồm chữ cái, chữ số, dấu chấm, gạch dưới và gạch ngang",
                },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Mã</FormLabel>
                  <FormControl>
                    <Input {...field} value={field.value ?? ""} />
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
                    <Input {...field} value={field.value ?? ""} />
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
                    <Textarea {...field} value={field.value ?? ""} rows={3} />
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
                      <Input {...field} type="email" value={field.value ?? ""} />
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
                      <Input {...field} value={field.value ?? ""} />
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
                    <Textarea {...field} value={field.value ?? ""} rows={2} />
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
                {form.formState.isSubmitting ? "Đang lưu…" : "Lưu"}
              </Button>
              <Button type="button" variant="outline" onClick={() => navigate(-1)}>
                Hủy
              </Button>
            </FormActions>
          </FormPanel>
        </form>
      </Form>
    </EditView>
  );
};
