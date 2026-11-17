import type { NotificationProvider } from "@refinedev/core";
import { toast } from "sonner";
import { getAccessToken } from "@/shared/api/session";
import { UndoableNotification } from "@/shared/components/notification/undoable-notification";

export function useNotificationProvider(): NotificationProvider {
  return {
    open: ({
      key,
      type,
      message,
      description,
      undoableTimeout,
      cancelMutation,
    }) => {
      switch (type) {
        case "success":
          toast.success(message, {
            id: key,
            description,
            richColors: true,
          });
          return;

        case "error":
          // Signing out leaves the screen's queries mid-flight; their 401s land after the session is gone, on the login
          // form, as "Lỗi (mã trạng thái: 401)". With no session there is nobody to tell, and the form is about to say
          // what it needs to. The login attempt's own failure (key "login-error") is the one that must still show.
          if (!getAccessToken() && !String(key ?? "").startsWith("login")) {
            return;
          }
          toast.error(message, {
            id: key,
            description,
            richColors: true,
          });
          return;

        case "progress": {
          const toastId = key || Date.now();

          toast(
            () => (
              <UndoableNotification
                message={message}
                description={description}
                undoableTimeout={undoableTimeout}
                cancelMutation={cancelMutation}
                onClose={() => toast.dismiss(toastId)}
              />
            ),
            {
              id: toastId,
              duration: (undoableTimeout || 5) * 1000,
              unstyled: true,
            }
          );
          return;
        }

        default:
          return;
      }
    },
    close: (id) => {
      toast.dismiss(id);
    },
  };
}
