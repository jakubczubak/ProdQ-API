package com.example.prodqapi.notification;

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

    ToolGroupAdded("New tool group created"),
    ToolGroupUpdated("Tool group details updated"),
    ToolGroupDeleted("Tool group deleted"),

    ToolAdded("Tool added to inventory"),
    ToolUpdated("Tool details updated"),
    ToolDeleted("Tool removed from inventory"),


    OrderAdded("New order placed"),
    OrderDeleted("Order removed from records"),
    OrderOnTheWay("Order is in transit"),
    OrderDelivered("Order successfully delivered"),
    OrderAllItemsReceived("All items received for order"),
    OrderPartiallyDelivered("Partial delivery processed for order"),
    OrderQualityRated("Quality rating added for order"),
    OrderInvoicePending("Order awaiting invoice"),
    OrderInvoiceReceived("Invoice received for order"),
    OrderClosed("Order closed"),
    OrderItemPriceUpdated("Item price updated in order"),
    OrderClosedShort("Order closed as incomplete delivery"),
    OrderMarkedForIncompleteClose("Order marked for incomplete closure - awaiting invoice"),
    InvoiceItemsEntered("Invoice line items entered for order"),
    InvoiceDiscrepanciesApproved("Invoice discrepancies approved for order"),

    SupplierAdded("New supplier added"),
    SupplierUpdated("Supplier details updated"),
    SupplierDeleted("Supplier removed from records"),

    BlockUser("User has been blocked"),
    UnblockUser("User has been unblocked"),
    GrantAdminPermission("User granted admin privileges"),
    RevokeAdminPermission("Admin privileges revoked from user"),
    DeleteUser("User has been deleted from the system"),
    UserUpdated("User details updated"),

    ToolQuantityUpdated("Tool quantity adjusted"),
    MaterialQuantityUpdated("Material quantity adjusted"),

    AccessoriesAdded("New accessories added"),
    AccessoriesUpdated("Accessories details updated"),
    AccessoriesDeleted("Accessories removed"),

    AccessoriesItemAdded("New accessories item added"),
    AccessoriesItemUpdated("Accessories item details updated"),
    AccessoriesItemDeleted("Accessories item removed"),

    DirectoryCleanupCompleted("Directory cleanup completed"),
    QueueSyncFailed("Failed to synchronize queue for a machine"),

    MachineAdded("New machine added"),
    MachineUpdated("Machine details updated"),
    MachineDeleted("Machine removed"),

    MrpCriticalShortage("Critical material shortage detected"),
    MrpHighPriorityItems("High priority items require attention");

    private final String description;

    NotificationDescription(String description) {
        this.description = description;
    }
}