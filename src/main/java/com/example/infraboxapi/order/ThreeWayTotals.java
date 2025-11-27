package com.example.infraboxapi.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Helper class for calculating three-way totals (PO, Delivery, Invoice)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreeWayTotals {

    private Double poNet;
    private Double poVat;
    private Double poGross;

    private Double deliveryNet;
    private Double deliveryVat;
    private Double deliveryGross;

    private Double invoiceNet;
    private Double invoiceVat;
    private Double invoiceGross;
}
