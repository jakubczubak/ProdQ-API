package com.example.infraboxapi.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for saving invoice items (contains list of items)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItemsDTO {

    private List<InvoiceItemDTO> items;
}
