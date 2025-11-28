package com.example.prodqapi.invoiceReconciliation;

import com.example.prodqapi.order.Order;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "invoice_reconciliation")
public class InvoiceReconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "order_id")
    @JsonBackReference("order-invoiceReconciliation")
    private Order order;

    @Column(name = "reconciliation_date")
    private String reconciliationDate;

    @Column(name = "reconciliation_status")
    private String reconciliationStatus;  // "matched", "discrepancy_pending", "discrepancy_approved"

    // Overall totals comparison
    @Column(name = "po_total_net")
    private Double poTotalNet;

    @Column(name = "delivery_total_net")
    private Double deliveryTotalNet;

    @Column(name = "invoice_total_net")
    private Double invoiceTotalNet;

    @Column(name = "po_total_vat")
    private Double poTotalVat;

    @Column(name = "delivery_total_vat")
    private Double deliveryTotalVat;

    @Column(name = "invoice_total_vat")
    private Double invoiceTotalVat;

    @Column(name = "po_total_gross")
    private Double poTotalGross;

    @Column(name = "delivery_total_gross")
    private Double deliveryTotalGross;

    @Column(name = "invoice_total_gross")
    private Double invoiceTotalGross;

    // Discrepancy details (stored as JSON)
    @Column(columnDefinition = "TEXT", name = "discrepancies_json")
    private String discrepanciesJson;  // Array of per-item discrepancies

    // User justification
    @Column(length = 1000, name = "discrepancy_justification")
    private String discrepancyJustification;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_date")
    private String approvedDate;
}
