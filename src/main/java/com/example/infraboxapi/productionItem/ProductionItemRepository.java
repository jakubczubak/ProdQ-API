package com.example.infraboxapi.productionItem;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductionItemRepository extends JpaRepository<ProductionItem, Integer> {
}
