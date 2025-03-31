package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import com.example.infraboxapi.productionQueue.ProductionQueue;
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

    private String type;            // "Mill" lub "Turn"
    private String subtype;         // "Plate", "Part", itd.
    private String orderName;       // "1077_LS_SAL"
    private String partName;        // "11_06_LSDAL7525"
    private int quantity;           // 1
    private String baseCamTime;     // "03:25:31"
    private String camTime;         // "03:25:31"
    private String deadline;        // null lub data w formacie "YYYY-MM-DD"
    private String additionalInfo;  // ""
    private String fileDirectory;   // "\\172.16.2.14\CNC\PROGRAMY CAM\1077_LS_SAL\CAM\11-06-TG_LSDAL7525_UCHWYT LOZYSKA"

    @Column(name = "queue_type")
    private String queueType;       // "ncQueue", "baca1", "baca2", "vensu350", "completed"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_queue_id")
    private ProductionQueue productionQueue;

    @OneToMany(mappedBy = "productionQueueItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductionFileInfo> files = new ArrayList<>(); // Lista powiązanych plików
}