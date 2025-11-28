package com.example.prodqapi.mrp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MrpDashboardDTO {

    // Counts by priority
    private long criticalCount;
    private long highCount;
    private long mediumCount;
    private long lowCount;
    private long totalCount;

    // Counts by resource type
    private long materialCount;
    private long toolCount;
    private long accessorieCount;

    // Financial summary
    private BigDecimal totalShortageValue;
    private BigDecimal criticalShortageValue;

    // Suggestion groups summary
    private long pendingSuggestionGroups;

    // Top critical items for quick view
    private List<MrpAnalysisResultDTO> topCriticalItems;

    // Last analysis timestamp
    private LocalDateTime lastAnalysisAt;

    // Status message
    private String statusMessage;
}
