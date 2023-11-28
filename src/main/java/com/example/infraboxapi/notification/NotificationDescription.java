package com.example.infraboxapi.notification;

import lombok.Getter;

@Getter
public enum NotificationDescription {
    MaterialAdded("New material activity."),
    MaterialUpdated("Material record updated."),
    MaterialDeleted("Material removed from the system."),

    MaterialGroupAdded("New material group activity."),
    MaterialGroupUpdated("Material group updated."),
    MaterialGroupDeleted("Material group removed from the system."),

    MaterialScanner("Material scanned."),
    ToolScanner("Tool scanned."),

    ToolGroupAdded("New tool group activity."),
    ToolGroupUpdated("Tool group updated."),
    ToolGroupDeleted("Tool group removed from the system."),

    ToolAdded("New tool activity."),
    ToolUpdated("Tool record updated."),
    ToolDeleted("Tool removed from the system."),

    DepartmentCostUpdated("Department cost updated."),

    CalculationAdded("New calculation activity."),
    CalculationUpdated("Calculation record updated."),
    CalculationDeleted("Calculation removed from the system."),

    OrderAdded("New order activity."),
    OrderDeleted("Order removed from the system."),
    OrderUpdated("Order record updated."),
    OrderOnTheWay("Order is on the way."),
    OrderDelivered("Order delivered."),

    RecyclingAdded("New recycling activity."),
    RecyclingUpdated("Recycling record updated."),
    RecyclingDeleted("Recycling removed from the system."),

    SupplierAdded("New supplier activity."),
    SupplierUpdated("Supplier record updated."),
    SupplierDeleted("Supplier removed from the system."),

    BlockUser("User blocked."),
    UnblockUser("User unblocked."),
    GrantAdminPermission("Admin privileges granted."),
    RevokeAdminPermission("Administrator privileges revoked."),
    DeleteUser("User has been removed from the system."),
    UserUpdated("User record updated."),
    UserCreated("New user created.");

    private final String description;

    NotificationDescription(String description) {
        this.description = description;
    }
}
