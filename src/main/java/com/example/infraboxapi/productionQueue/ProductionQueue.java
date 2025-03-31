package com.example.infraboxapi.productionQueue;

import com.example.infraboxapi.productionQueueItem.ProductionQueueItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_production_queue")
public class ProductionQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;              // Unikalny identyfikator (tylko jeden rekord)

    @OneToMany(mappedBy = "productionQueue", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductionQueueItem> items = new ArrayList<>(); // Wszystkie elementy w jednej liście

    // Dynamiczne listy oparte na queueType
    @Transient
    public List<ProductionQueueItem> getNcQueue() {
        return items.stream()
                .filter(item -> "ncQueue".equals(item.getQueueType()))
                .collect(Collectors.toList());
    }

    @Transient
    public List<ProductionQueueItem> getBaca1() {
        return items.stream()
                .filter(item -> "baca1".equals(item.getQueueType()))
                .collect(Collectors.toList());
    }

    @Transient
    public List<ProductionQueueItem> getBaca2() {
        return items.stream()
                .filter(item -> "baca2".equals(item.getQueueType()))
                .collect(Collectors.toList());
    }

    @Transient
    public List<ProductionQueueItem> getVensu350() {
        return items.stream()
                .filter(item -> "vensu350".equals(item.getQueueType()))
                .collect(Collectors.toList());
    }

    @Transient
    public List<ProductionQueueItem> getCompleted() {
        return items.stream()
                .filter(item -> "completed".equals(item.getQueueType()))
                .collect(Collectors.toList());
    }
}