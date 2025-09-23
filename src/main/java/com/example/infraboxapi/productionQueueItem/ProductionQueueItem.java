package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.materialType.MaterialType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_production_queue_item")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ProductionQueueItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String type;
    private String subtype;

    @Column(nullable = false)
    @NotBlank(message = "Order name cannot be blank")
    private String orderName;

    @Column(nullable = false)
    @NotBlank(message = "Part name cannot be blank")
    private String partName;

    private int quantity;
    private String baseCamTime;
    private String camTime;
    private String deadline;
    @ElementCollection
    private List<String> selectedDays;

    private String additionalInfo;
    private String fileDirectory;
    private String author;
    @Column(name = "completed", nullable = false)
    private boolean completed = false;

    @Column(name = "queue_type")
    private String queueType;

    @Column(name = "order_position")
    private Integer order;

    @Column(name = "last_modified")
    private LocalDateTime lastModified;

    private String material;
    private String materialValue;
    private String materialProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_type_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private MaterialType materialType;

    private Float materialPricePerKg;

    // Dimension fields
    private Float x;
    private Float y;
    private Float z;
    private Float diameter;
    private Float innerDiameter;
    private Float length;

    // DODANE POLA ZALEŻNOŚCI
    @Column(name = "depends_on_id")
    private Integer dependsOnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depends_on_id", insertable = false, updatable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "successors"})
    private ProductionQueueItem predecessor;

    @OneToMany(mappedBy = "predecessor", fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "predecessor"})
    private List<ProductionQueueItem> successors = new ArrayList<>();


    @OneToMany(mappedBy = "productionQueueItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<ProductionFileInfo> files = new ArrayList<>();
}