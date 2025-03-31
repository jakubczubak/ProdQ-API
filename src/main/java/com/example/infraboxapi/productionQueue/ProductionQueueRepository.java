package com.example.infraboxapi.productionQueue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductionQueueRepository extends JpaRepository<ProductionQueue, Integer> {
}
