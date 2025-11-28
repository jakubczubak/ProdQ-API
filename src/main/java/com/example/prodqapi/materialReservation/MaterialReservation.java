package com.example.prodqapi.materialReservation;

import com.example.prodqapi.material.Material;
import com.example.prodqapi.materialType.MaterialType;
import com.example.prodqapi.productionQueueItem.ProductionQueueItem;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
    @JsonIgnore // Nie wysyłaj productionQueueItem do frontendu
    private ProductionQueueItem productionQueueItem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "material_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Material material;

    @Column(name = "is_custom", nullable = false)
    @Builder.Default
    private Boolean isCustom = false;

    @Column(name = "custom_name")
    private String customName;

    @Enumerated(EnumType.STRING)
    @Column(name = "custom_type")
    private MaterialProfile customType;

    // Custom Plate fields
    @Column(name = "custom_x")
    private Double customX;

    @Column(name = "custom_y")
    private Double customY;

    @Column(name = "custom_z")
    private Double customZ;

    // Custom Rod fields
    @Column(name = "custom_diameter")
    private Double customDiameter;

    @Column(name = "custom_length")
    private Double customLength;  // Length for custom rods/tubes

    // Custom Tube fields (reuses customDiameter as outer diameter)
    @Column(name = "custom_inner_diameter")
    private Double customInnerDiameter;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "custom_material_type_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private MaterialType customMaterialType;

    // Reservation amount - separated by material type
    @Column(name = "reserved_quantity")
    private Integer reservedQuantity;  // For Plates: number of pieces

    @Column(name = "reserved_length")
    private Double reservedLength;  // For Rods/Tubes: length in mm

    @Column(name = "weight")
    private Double weight;

    @Column(name = "cost")
    private Double cost;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
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