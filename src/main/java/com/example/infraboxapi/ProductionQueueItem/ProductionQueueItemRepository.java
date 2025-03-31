package com.example.infraboxapi.ProductionQueueItemService;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductionQueueItemRepository extends JpaRepository<ProductionQueueItem, String> {
}