package com.example.prodqapi.documentAttachment;

/**
 * Categories for document attachments in orders.
 *
 * Supported categories:
 * - INVOICE: Faktury (invoices)
 * - DELIVERY_NOTE: WZ (delivery notes/waybills)
 * - CERTIFICATE: Atesty/Certyfikaty (quality certificates)
 * - PHOTO: Zdjęcia (photos/images)
 */
public enum DocumentCategory {
    INVOICE("Faktura"),
    DELIVERY_NOTE("WZ"),
    CERTIFICATE("Atest/Certyfikat"),
    PHOTO("Zdjęcia");

    private final String displayName;

    DocumentCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
