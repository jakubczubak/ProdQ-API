package com.example.infraboxapi.productionQueueItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ProductionQueueItemRepository extends JpaRepository<ProductionQueueItem, Integer> {
    List<ProductionQueueItem> findByQueueType(String queueType);

    @Query("SELECT COALESCE(MAX(p.order), -1) FROM ProductionQueueItem p WHERE p.queueType = :queueType")
    Integer findMaxOrderByQueueType(String queueType);

    @Query("SELECT p FROM ProductionQueueItem p LEFT JOIN FETCH p.files f WHERE p.id = :id ORDER BY f.order ASC")
    Optional<ProductionQueueItem> findByIdWithFiles(@Param("id") Integer id);

    @Query("SELECT DISTINCT f.fileName FROM ProductionQueueItem p JOIN p.files f WHERE p.orderName = :orderName AND p.partName = :partName")
    Set<String> findFileNamesByOrderNameAndPartName(String orderName, String partName);
}