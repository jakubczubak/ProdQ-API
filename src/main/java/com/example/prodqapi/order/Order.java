package com.example.prodqapi.order;

import com.example.prodqapi.documentAttachment.DocumentAttachment;
import com.example.prodqapi.orderChangeLog.OrderChangeLog;
import com.example.prodqapi.orderItem.OrderItem;
import com.example.prodqapi.supplier.Supplier;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private String date;
    private String status;
    private String supplierEmail;

    @Lob  // Oznaczenie pola jako typu Lob (Large Object)
    private String supplierMessage;

    @Column(name = "total_net")
    private double totalNet; // Total net price (before VAT)

    @Column(name = "total_vat")
    private double totalVat; // Total VAT amount

    @Column(name = "total_gross")
    private double totalGross; // Total gross price (net + VAT)

    private boolean externalQuantityUpdated;
    private boolean transitQuantitySet;

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    private String expectedDeliveryDate;

    @Column(name = "tracking_number")
    private String trackingNumber;

    // === Supplier Performance Tracking Fields ===

    /**
     * Actual delivery date (when order was fully delivered)
     * Set automatically when status changes to 'delivered'
     */
    @Column(name = "actual_delivery_date")
    private String actualDeliveryDate;

    /**
     * Quality rating (1-5 stars) given by user when confirming delivery
     * null if not yet rated
     */
    @Column(name = "quality_rating")
    private Integer qualityRating;

    /**
     * Optional notes about delivery quality
     */
    @Column(name = "quality_notes", length = 500)
    private String qualityNotes;

    /**
     * @deprecated Use DocumentAttachment entity with category=INVOICE instead.
     * This field is kept for backwards compatibility with existing data.
     */
    @Deprecated
    @Column(name = "invoice_file_name")
    private String invoiceFileName; // Original filename of uploaded invoice

    /**
     * @deprecated Use DocumentAttachment entity with category=INVOICE instead.
     * This field is kept for backwards compatibility with existing data.
     */
    @Deprecated
    @Column(name = "invoice_file_path")
    private String invoiceFilePath; // Server path to invoice file

    /**
     * @deprecated Use DocumentAttachment entity with category=INVOICE instead.
     * This field is kept for backwards compatibility with existing data.
     */
    @Deprecated
    @Column(name = "invoice_upload_date")
    private String invoiceUploadDate; // Date when invoice was uploaded

    @Column(name = "invoice_received_date")
    private String invoiceReceivedDate; // Date when invoice was marked as received (MES workflow)

    @Column(name = "closed_date")
    private String closedDate; // Date when order was closed (final status)

    @Column(name = "closed_short_date")
    private String closedShortDate; // Date when order was closed as incomplete

    @Column(name = "closed_short_reason", length = 500)
    private String closedShortReason; // Reason for closing order as incomplete (required, min 10 chars)

    @Column(name = "pending_closed_short")
    private Boolean pendingClosedShort = false; // Flag: awaiting invoice before closing as incomplete

    @Column(name = "closed_no_invoice_date")
    private String closedNoInvoiceDate; // Date when order was closed without invoice

    @Column(name = "closed_no_invoice_reason", length = 500)
    private String closedNoInvoiceReason; // Reason for closing without invoice (required, min 10 chars)

    @Column(name = "invoice_data_entered")
    private Boolean invoiceDataEntered = false; // Flag: invoice line items have been entered

    @Column(name = "invoice_reconciliation_completed")
    private Boolean invoiceReconciliationCompleted = false; // Flag: three-way match reconciliation completed

    private String createdBy;
    private String lastModifiedBy;

    @Column(name = "created_date")
    private String createdDate;

    @Column(name = "last_modified_date")
    private String lastModifiedDate;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id")
    private List<OrderItem> orderItems;

    @OneToMany
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    @OrderBy("date DESC")
    private List<OrderChangeLog> changeLog;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id")
    @JsonManagedReference("order-invoiceItems")
    private List<com.example.prodqapi.invoiceItem.InvoiceItem> invoiceItems;

    @OneToOne(cascade = CascadeType.ALL, mappedBy = "order")
    @JsonManagedReference("order-invoiceReconciliation")
    private com.example.prodqapi.invoiceReconciliation.InvoiceReconciliation invoiceReconciliation;

    /**
     * Document attachments (invoices, delivery notes, certificates, photos)
     * Ordered by displayOrder ASC, uploadDate DESC
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, uploadDate DESC")
    @JsonManagedReference("order-documents")
    @Builder.Default
    private List<DocumentAttachment> documents = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername = userDetails.getUsername();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        createdDate = now.format(formatter);
        createdBy = currentUsername;
    }

    @PreUpdate
    public void preUpdate() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername = userDetails.getUsername();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        lastModifiedDate = now.format(formatter);
        lastModifiedBy = currentUsername;
    }
}
