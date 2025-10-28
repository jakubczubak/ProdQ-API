package com.example.infraboxapi.materialReservation;

import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.materialType.MaterialType;
import com.example.infraboxapi.productionQueueItem.ProductionQueueItem;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "material_reservations")
public class MaterialReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_queue_item_id")
    private ProductionQueueItem productionQueueItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    private Material material;

    @Column(name = "is_custom", nullable = false)
    private Boolean isCustom = false;

    @Column(name = "custom_name")
    private String customName;

    @Enumerated(EnumType.STRING)
    @Column(name = "custom_type")
    private MaterialProfile customType;

    @Column(name = "custom_x")
    private Double customX;

    @Column(name = "custom_y")
    private Double customY;

    @Column(name = "custom_z")
    private Double customZ;

    @Column(name = "custom_diameter")
    private Double customDiameter;

    @Column(name = "custom_inner_diameter")
    private Double customInnerDiameter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_material_type_id")
    private MaterialType customMaterialType;

    @Column(name = "quantity_or_length", nullable = false)
    private Double quantityOrLength;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "cost")
    private Double cost;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status = ReservationStatus.RESERVED;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = ReservationStatus.RESERVED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}