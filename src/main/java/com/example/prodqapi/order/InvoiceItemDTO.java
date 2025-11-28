package com.example.prodqapi.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for individual invoice item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItemDTO {

    private Integer orderItemId;
    private Double invoiceQuantity;
    private Double invoiceUnitPrice;
    private Double invoicePricePerKg;
    private Double invoiceVatRate;
    private Double invoiceDiscount;
}
