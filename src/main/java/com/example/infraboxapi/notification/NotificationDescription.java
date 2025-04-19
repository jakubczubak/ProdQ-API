package com.example.infraboxapi.notification;

import lombok.Getter;

@Getter
public enum NotificationDescription {
    MaterialAdded("Material added to the inventory"),
    MaterialUpdated("Material details updated"),
    MaterialDeleted("Material removed from inventory"),

    MaterialGroupAdded("New material group created"),
    MaterialGroupUpdated("Material group details updated"),
    MaterialGroupDeleted("Material group deleted"),

    MaterialTypeAdded("New material type created"),
    MaterialTypeUpdated("Material type details updated"),
    MaterialTypeDeleted("Material type deleted"),

    MaterialScanner("Materials have been successfully scanned"),
    ToolScanner("Tool has been successfully scanned"),

    ToolGroupAdded("New tool group created"),
    ToolGroupUpdated("Tool group details updated"),
    ToolGroupDeleted("Tool group deleted"),

    ToolAdded("Tool added to inventory"),
    ToolUpdated("Tool details updated"),
    ToolDeleted("Tool removed from inventory"),

    DepartmentCostUpdated("Department cost adjusted"),

    OrderAdded("New order placed"),
    OrderDeleted("Order removed from records"),
    OrderUpdated("Order details updated"),
    OrderOnTheWay("Order is in transit"),
    OrderDelivered("Order successfully delivered"),

    RecyclingAdded("New recycling entry created"),
    RecyclingUpdated("Recycling entry updated"),
    RecyclingDeleted("Recycling entry removed"),

    SupplierAdded("New supplier added"),
    SupplierUpdated("Supplier details updated"),
    SupplierDeleted("Supplier removed from records"),

    BlockUser("User has been blocked"),
    UnblockUser("User has been unblocked"),
    GrantAdminPermission("User granted admin privileges"),
    RevokeAdminPermission("Admin privileges revoked from user"),
    DeleteUser("User has been deleted from the system"),
    UserUpdated("User details updated"),
    UserCreated("New user account created"),

    ProductionItemAdded("New production item added"),
    ProductionItemUpdated("Production item details updated"),
    ProductionItemDeleted("Production item removed"),

    ProjectAdded("New project initiated"),
    ProjectUpdated("Project details updated"),
    ProjectDeleted("Project deleted"),
    ProjectStatusUpdated("Project status has changed"),
    HourlyRateUpdated("Hourly rate has been updated"),

    ToolQuantityUpdated("Tool quantity adjusted"),
    MaterialQuantityUpdated("Material quantity adjusted"),

    AccessoriesAdded("New accessories added"),
    AccessoriesUpdated("Accessories details updated"),
    AccessoriesDeleted("Accessories removed"),

    AccessoriesItemAdded("New accessories item added"),
    AccessoriesItemUpdated("Accessories item details updated"),
    AccessoriesItemDeleted("Accessories item removed"),
    AccessorieItemScanner("Accessories have been successfully scanned"),

    DirectoryCleanupCompleted("Directory cleanup completed");

    private final String description;

    NotificationDescription(String description) {
        this.description = description;
    }
}