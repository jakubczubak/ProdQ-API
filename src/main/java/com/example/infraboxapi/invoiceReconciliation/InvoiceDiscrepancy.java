package com.example.infraboxapi.invoiceReconciliation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POJO for representing discrepancies in three-way match
 * Used for JSON serialization in InvoiceReconciliation.discrepanciesJson
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDiscrepancy {

    private Integer orderItemId;
    private String itemName;

    // PO values
    private Double poQuantity;
    private Double poUnitPrice;

    // Delivery values
    private Double deliveryQuantity;
    private Double deliveryUnitPrice;

    // Invoice values
    private Double invoiceQuantity;
    private Double invoiceUnitPrice;

    // Differences
    private Double quantityDifference;  // invoice - delivery
    private Double priceDifference;     // invoice - delivery
    private Double amountDifference;    // Total impact

    private String discrepancyType;  // "quantity", "price", "both"
    private String severity;         // "minor", "moderate", "major"
}