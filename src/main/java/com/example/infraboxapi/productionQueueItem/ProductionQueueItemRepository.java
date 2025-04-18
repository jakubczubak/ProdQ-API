package com.example.infraboxapi.productionQueueItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductionQueueItemRepository extends JpaRepository<ProductionQueueItem, Integer> {
    List<ProductionQueueItem> findByQueueType(String queueType);

    @Query("SELECT COALESCE(MAX(p.order), -1) FROM ProductionQueueItem p WHERE p.queueType = :queueType")
    Integer findMaxOrderByQueueType(String queueType);

    List<ProductionQueueItem> findByOrderName(String orderName);

    @Query("SELECT p FROM ProductionQueueItem p WHERE p.orderName = :orderName AND p.partName != :partName")
    List<ProductionQueueItem> findByOrderNameAndDifferentPartName(String orderName, String partName);

    @Query("SELECT p FROM ProductionQueueItem p LEFT JOIN FETCH p.files WHERE p.id = :id")
    Optional<ProductionQueueItem> findByIdWithFiles(@Param("id") Integer id);

    List<ProductionQueueItem> findByOrderNameAndPartName(String orderName, String partName);
}