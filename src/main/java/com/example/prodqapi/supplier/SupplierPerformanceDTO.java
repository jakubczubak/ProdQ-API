package com.example.prodqapi.supplier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for supplier performance metrics.
 * Used in API responses for supplier scorecard and ranking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPerformanceDTO {

    private Integer id;
    private Integer supplierId;
    private String supplierName;
    private String companyName;

    // === KPI Values ===

    /**
     * On-time delivery rate (0-100%)
     */
    private Double onTimeDeliveryRate;

    /**
     * Average lead time in days
     */
    private Double avgLeadTimeDays;

    /**
     * Quality rate (0-100%)
     */
    private Double qualityRate;

    /**
     * Lead time score component (0-100)
     */
    private Double leadTimeScore;

    /**
     * Overall score (0-100)
     */
    private Double overallScore;

    // === Counters ===

    private Integer totalOrders;
    private Integer completedOrders;
    private Integer onTimeOrders;
    private Integer lateOrders;
    private Integer qualityRatingsCount;

    // === Derived fields ===

    /**
     * Score category: EXCELLENT (80+), GOOD (60-79), AVERAGE (40-59), POOR (<40)
     */
    private String scoreCategory;

    /**
     * Average quality rating (1-5 stars)
     */
    private Double avgQualityRating;

    // === Metadata ===

    private LocalDateTime lastCalculatedAt;

    /**
     * Convert entity to DTO
     */
    public static SupplierPerformanceDTO fromEntity(SupplierPerformance entity) {
        if (entity == null) {
            return null;
        }

        Supplier supplier = entity.getSupplier();
        String supplierName = supplier != null ? supplier.getName() + " " + supplier.getSurname() : null;
        String companyName = supplier != null ? supplier.getCompanyName() : null;

        // Calculate average quality rating from sum and count
        Double avgQualityRating = null;
        if (entity.getQualityRatingsCount() != null && entity.getQualityRatingsCount() > 0
                && entity.getTotalQualityRatingSum() != null) {
            avgQualityRating = entity.getTotalQualityRatingSum() / entity.getQualityRatingsCount();
        }

        // Determine score category
        String scoreCategory = determineScoreCategory(entity.getOverallScore());

        return SupplierPerformanceDTO.builder()
                .id(entity.getId())
                .supplierId(supplier != null ? supplier.getId() : null)
                .supplierName(supplierName)
                .companyName(companyName)
                .onTimeDeliveryRate(entity.getOnTimeDeliveryRate())
                .avgLeadTimeDays(entity.getAvgLeadTimeDays())
                .qualityRate(entity.getQualityRate())
                .leadTimeScore(entity.getLeadTimeScore())
                .overallScore(entity.getOverallScore())
                .totalOrders(entity.getTotalOrders())
                .completedOrders(entity.getCompletedOrders())
                .onTimeOrders(entity.getOnTimeOrders())
                .lateOrders(entity.getLateOrders())
                .qualityRatingsCount(entity.getQualityRatingsCount())
                .scoreCategory(scoreCategory)
                .avgQualityRating(avgQualityRating)
                .lastCalculatedAt(entity.getLastCalculatedAt())
                .build();
    }

    /**
     * Determine score category based on overall score
     */
    private static String determineScoreCategory(Double score) {
        if (score == null) {
            return "NO_DATA";
        }
        if (score >= 80) {
            return "EXCELLENT";
        } else if (score >= 60) {
            return "GOOD";
        } else if (score >= 40) {
            return "AVERAGE";
        } else {
            return "POOR";
        }
    }
}
