package com.example.infraboxapi.order;

import com.example.infraboxapi.invoiceReconciliation.InvoiceDiscrepancy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for three-way match reconciliation results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceReconciliationDTO {

    private Integer orderId;
    private String reconciliationDate;
    private String status;

    // Totals summary
    private TotalsDTO totals;

    // List of discrepancies
    private List<InvoiceDiscrepancy> discrepancies;

    // Flag indicating if there are discrepancies
    private boolean hasDiscrepancies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TotalsDTO {
        private ThreeWayTotal po;
        private ThreeWayTotal delivery;
        private ThreeWayTotal invoice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreeWayTotal {
        private Double net;
        private Double vat;
        private Double gross;
    }
}
