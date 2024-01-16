package com.example.infraboxapi.productionItem;

import com.example.infraboxapi.FilePDF.FilePDF;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ProductionItemDTO {
    private Integer id;
    private String partName;
    private Integer quantity;
    private String updatedOn;
    private String status;
    private double camTime;
    private BigDecimal materialValue;
    private String partType;
    private FilePDF filePDF;
}
