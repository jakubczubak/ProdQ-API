package com.example.prodqapi.invoiceItem;

import com.example.prodqapi.order.Order;
import com.example.prodqapi.orderItem.OrderItem;
import com.fasterxml.jackson.annotation.JsonBackReference;
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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "invoice_item")
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonBackReference("order-invoiceItems")
    private Order order;

    @ManyToOne
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;  // Link to original PO item

    @Column(name = "invoice_quantity")
    private Double invoiceQuantity;     // Quantity on invoice

    @Column(name = "invoice_unit_price")
    private Double invoiceUnitPrice;    // Unit price on invoice

    @Column(name = "invoice_price_per_kg")
    private Double invoicePricePerKg;   // Price PLN/kg (for materials)

    @Column(name = "invoice_vat_rate")
    private Double invoiceVatRate;      // VAT rate on invoice

    @Column(name = "invoice_discount")
    private Double invoiceDiscount;     // Discount on invoice

    // Computed fields
    @Column(name = "invoice_net_amount")
    private Double invoiceNetAmount;    // Net amount (quantity * price)

    @Column(name = "invoice_vat_amount")
    private Double invoiceVatAmount;    // VAT amount

    @Column(name = "invoice_gross_amount")
    private Double invoiceGrossAmount;  // Gross amount

    @Column(name = "created_date")
    private String createdDate;

    @Column(name = "created_by")
    private String createdBy;

    @PrePersist
    public void prePersist() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername = userDetails.getUsername();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        createdDate = now.format(formatter);
        createdBy = currentUsername;
    }
}
