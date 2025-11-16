package com.example.infraboxapi.order;

import com.example.infraboxapi.orderItem.OrderItem;
import com.example.infraboxapi.supplier.Supplier;
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

    @Column(name = "invoice_file_name")
    private String invoiceFileName; // Original filename of uploaded invoice

    @Column(name = "invoice_file_path")
    private String invoiceFilePath; // Server path to invoice file

    @Column(name = "invoice_upload_date")
    private String invoiceUploadDate; // Date when invoice was uploaded

    private String createdBy;
    private String lastModifiedBy;

    @Column(name = "created_date")
    private String createdDate;

    @Column(name = "last_modified_date")
    private String lastModifiedDate;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id")
    private List<OrderItem> orderItems;

    @PrePersist
    public void prePersist() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername = userDetails.getUsername();
        if (!"root@gmail.com".equals(currentUsername)) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            createdDate = now.format(formatter);
            createdBy = currentUsername;
        }
    }

    @PreUpdate
    public void preUpdate() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername = userDetails.getUsername();
        if (!"root@gmail.com".equals(currentUsername)) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            lastModifiedDate = now.format(formatter);
            lastModifiedBy = currentUsername;
        }
    }
}
