package com.example.prodqapi.mrp;

import com.example.prodqapi.supplier.Supplier;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a single MRP analysis result for a resource.
 * Each result identifies a potential shortage or stock issue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mrp_analysis_result")
public class MrpAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private Integer resourceId;

    @Column(name = "resource_name")
    private String resourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private MrpPriority priority;

    @Column(name = "current_stock", precision = 10, scale = 2)
    private BigDecimal currentStock;

    @Column(name = "reserved_quantity", precision = 10, scale = 2)
    private BigDecimal reservedQuantity;

    @Column(name = "available_quantity", precision = 10, scale = 2)
    private BigDecimal availableQuantity;

    @Column(name = "in_transit", precision = 10, scale = 2)
    private BigDecimal inTransit;

    @Column(name = "min_quantity", precision = 10, scale = 2)
    private BigDecimal minQuantity;

    @Column(name = "required_quantity", precision = 10, scale = 2)
    private BigDecimal requiredQuantity;

    @Column(name = "shortage_quantity", precision = 10, scale = 2)
    private BigDecimal shortageQuantity;

    @Column(name = "suggested_order_qty", precision = 10, scale = 2)
    private BigDecimal suggestedOrderQty;

    @Column(name = "unit")
    private String unit;  // "szt" for pieces, "mm" for length

    @Column(name = "earliest_need_date")
    private LocalDate earliestNeedDate;

    @Column(name = "affected_productions", columnDefinition = "TEXT")
    private String affectedProductions;  // JSON array of affected production IDs/names

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MrpAnalysisStatus status = MrpAnalysisStatus.PENDING;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggestion_group_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "analyses"})
    private MrpOrderSuggestionGroup suggestionGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_supplier_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Supplier preferredSupplier;

    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @PrePersist
    protected void onCreate() {
        if (analyzedAt == null) {
            analyzedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = MrpAnalysisStatus.PENDING;
        }
    }
}
