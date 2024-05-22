package com.example.infraboxapi.productionItemMaterial;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductionItemMaterialRepository extends JpaRepository<ProductionItemMaterial, Integer> {
}
