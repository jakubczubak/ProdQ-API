package com.example.infraboxapi.mrp;

import com.example.infraboxapi.order.Order;
import com.example.infraboxapi.supplier.Supplier;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a grouped order suggestion from MRP analysis.
 * Groups multiple analysis results by supplier and resource type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mrp_order_suggestion_group")
public class MrpOrderSuggestionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "highest_priority", nullable = false)
    private MrpPriority highestPriority;

    @OneToMany(mappedBy = "suggestionGroup", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "suggestionGroup"})
    @Builder.Default
    private List<MrpAnalysisResult> analyses = new ArrayList<>();

    @Column(name = "item_count")
    private Integer itemCount;

    @Column(name = "estimated_total_net", precision = 12, scale = 2)
    private BigDecimal estimatedTotalNet;

    @Column(name = "estimated_total_gross", precision = 12, scale = 2)
    private BigDecimal estimatedTotalGross;

    @Column(name = "estimated_lead_time_days")
    private Integer estimatedLeadTimeDays;

    @Column(name = "suggested_order_date")
    private LocalDate suggestedOrderDate;

    @Column(name = "earliest_need_date")
    private LocalDate earliestNeedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_order_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "orderItems"})
    private Order generatedOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    @Column(name = "dismissed_reason")
    private String dismissedReason;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = SuggestionStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Add an analysis result to this group
     */
    public void addAnalysis(MrpAnalysisResult analysis) {
        if (analyses == null) {
            analyses = new ArrayList<>();
        }
        analyses.add(analysis);
        analysis.setSuggestionGroup(this);
        updateItemCount();
        updateHighestPriority(analysis.getPriority());
        updateEarliestNeedDate(analysis.getEarliestNeedDate());
    }

    private void updateItemCount() {
        this.itemCount = analyses != null ? analyses.size() : 0;
    }

    private void updateHighestPriority(MrpPriority newPriority) {
        if (highestPriority == null || newPriority.getOrder() < highestPriority.getOrder()) {
            highestPriority = newPriority;
        }
    }

    private void updateEarliestNeedDate(LocalDate needDate) {
        if (needDate != null) {
            if (earliestNeedDate == null || needDate.isBefore(earliestNeedDate)) {
                earliestNeedDate = needDate;
            }
        }
    }
}
