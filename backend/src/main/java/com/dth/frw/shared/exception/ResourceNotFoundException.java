package com.dth.frw.shared.exception;

/** Raised when a resource is addressed by an id that does not exist. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("Không tìm thấy %s có mã %s".formatted(display(resource), id));
    }

    private static String display(String resource) {
        if (resource == null) {
            return "đối tượng";
        }
        return switch (resource) {
            case "Organization" -> "đơn vị";
            case "Academic year" -> "năm học";
            case "User" -> "người dùng";
            case "Role" -> "vai trò";
            case "Permission" -> "quyền";
            case "Notification" -> "thông báo";
            case "Department" -> "phòng ban";
            case "Row level policy", "Row-level policy" -> "chính sách quyền dữ liệu";
            default -> resource;
        };
    }
}
