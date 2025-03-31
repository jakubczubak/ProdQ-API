package com.example.infraboxapi.productionQueueItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductionQueueItemRepository extends JpaRepository<ProductionQueueItem, Integer> {
}