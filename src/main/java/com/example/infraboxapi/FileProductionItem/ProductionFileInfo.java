package com.example.infraboxapi.FileProductionItem;

import com.example.infraboxapi.productionQueueItem.ProductionQueueItem;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_production_file_info")
public class ProductionFileInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String fileType;
    private Long fileSize; // Dodane nowe pole

    @Lob
    private byte[] fileContent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_queue_item_id")
    @JsonBackReference
    private ProductionQueueItem productionQueueItem;

    @Column(name = "completed", nullable = false)
    private boolean completed = false;
}