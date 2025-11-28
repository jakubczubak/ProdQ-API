package com.example.prodqapi.mrp.dto;

import com.example.prodqapi.mrp.MrpPriority;
import com.example.prodqapi.mrp.ResourceType;
import com.example.prodqapi.mrp.SuggestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MrpOrderSuggestionGroupDTO {
    private Integer id;
    private String groupName;

    private Integer supplierId;
    private String supplierName;
    private String supplierCompanyName;

    private ResourceType resourceType;
    private String resourceTypeDisplay;

    private MrpPriority highestPriority;
    private String priorityDescription;

    private Integer itemCount;
    private BigDecimal estimatedTotalNet;
    private BigDecimal estimatedTotalGross;

    private Integer estimatedLeadTimeDays;
    private LocalDate suggestedOrderDate;
    private LocalDate earliestNeedDate;

    private SuggestionStatus status;
    private LocalDateTime createdAt;

    private List<MrpAnalysisResultDTO> analyses;

    private Integer generatedOrderId;
}
