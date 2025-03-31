package com.example.infraboxapi.FileProductionItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductionFileInfoRepository extends JpaRepository<ProductionFileInfo, Long> {
}