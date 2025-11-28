package com.example.prodqapi.mrp.dto;

import com.example.prodqapi.mrp.MrpAnalysisStatus;
import com.example.prodqapi.mrp.MrpPriority;
import com.example.prodqapi.mrp.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MrpAnalysisResultDTO {
    private Integer id;
    private ResourceType resourceType;
    private Integer resourceId;
    private String resourceName;
    private MrpPriority priority;
    private String priorityDescription;

    private BigDecimal currentStock;
    private BigDecimal reservedQuantity;
    private BigDecimal availableQuantity;
    private BigDecimal inTransit;
    private BigDecimal minQuantity;
    private BigDecimal requiredQuantity;
    private BigDecimal shortageQuantity;
    private BigDecimal suggestedOrderQty;
    private String unit;

    private LocalDate earliestNeedDate;
    private String affectedProductions;

    private MrpAnalysisStatus status;
    private LocalDateTime analyzedAt;

    private Integer preferredSupplierId;
    private String preferredSupplierName;
    private BigDecimal estimatedCost;

    private Integer suggestionGroupId;
}
