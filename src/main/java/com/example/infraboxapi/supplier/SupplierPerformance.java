package com.example.infraboxapi.supplier;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity storing calculated KPI metrics for suppliers.
 * Recalculated nightly by SupplierPerformanceScheduler.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "supplier_performance")
public class SupplierPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", unique = true)
    private Supplier supplier;

    // === KPI Fields ===

    /**
     * On-time delivery rate (0-100%)
     * Formula: (onTimeOrders / completedOrders) * 100
     */
    @Column(name = "on_time_delivery_rate")
    private Double onTimeDeliveryRate;

    /**
     * Average lead time in days
     * Formula: AVG(actualDeliveryDate - orderDate) for completed orders
     */
    @Column(name = "avg_lead_time_days")
    private Double avgLeadTimeDays;

    /**
     * Quality rate (0-100%)
     * Formula: (avgQualityRating / 5) * 100
     */
    @Column(name = "quality_rate")
    private Double qualityRate;

    /**
     * Lead time score for overall calculation (0-100)
     * Formula: max(0, 100 - (avgLeadTimeDays - 7) * 3)
     * 100 for ≤7 days, decreasing by 3 per day, 0 for ≥40 days
     */
    @Column(name = "lead_time_score")
    private Double leadTimeScore;

    /**
     * Overall score (0-100)
     * Formula: (onTimeDeliveryRate * 0.5) + (qualityRate * 0.3) + (leadTimeScore * 0.2)
     */
    @Column(name = "overall_score")
    private Double overallScore;

    // === Counters ===

    /**
     * Total number of orders placed with this supplier
     */
    @Column(name = "total_orders")
    private Integer totalOrders;

    /**
     * Number of completed (delivered) orders
     */
    @Column(name = "completed_orders")
    private Integer completedOrders;

    /**
     * Number of orders delivered on time (actualDeliveryDate <= expectedDeliveryDate)
     */
    @Column(name = "on_time_orders")
    private Integer onTimeOrders;

    /**
     * Number of late orders (actualDeliveryDate > expectedDeliveryDate)
     */
    @Column(name = "late_orders")
    private Integer lateOrders;

    /**
     * Sum of all quality ratings received (for calculating average)
     */
    @Column(name = "total_quality_rating_sum")
    private Double totalQualityRatingSum;

    /**
     * Count of orders with quality ratings (for calculating average)
     */
    @Column(name = "quality_ratings_count")
    private Integer qualityRatingsCount;

    // === Metadata ===

    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
