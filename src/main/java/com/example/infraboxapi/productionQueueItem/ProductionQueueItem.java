package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_production_queue_item")
public class ProductionQueueItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String type;
    private String subtype;
    private String orderName;
    private String partName;
    private int quantity;
    private String baseCamTime;
    private String camTime;
    private String deadline;
    private String additionalInfo;
    private String fileDirectory;
    private String author;

    @Column(name = "queue_type")
    private String queueType;

    @OneToMany(mappedBy = "productionQueueItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<ProductionFileInfo> files = new ArrayList<>();
}