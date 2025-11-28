package com.example.prodqapi.FileProductionItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionFileInfoRepository extends JpaRepository<ProductionFileInfo, Long> {

    @Query("SELECT f FROM ProductionFileInfo f LEFT JOIN FETCH f.productionQueueItem")
    List<ProductionFileInfo> findAllWithQueueItem();
}