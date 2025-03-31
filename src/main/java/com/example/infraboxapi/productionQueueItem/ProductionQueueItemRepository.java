package com.example.infraboxapi.productionQueueItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionQueueItemRepository extends JpaRepository<ProductionQueueItem, Integer> {
    List<ProductionQueueItem> findByQueueType(String queueType);
}