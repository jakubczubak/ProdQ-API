package com.example.infraboxapi.material;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Integer> {
    @Query("SELECT m FROM Material m WHERE m.quantity < m.minQuantity")
    List<Material> findByQuantityLessThanMinQuantity();
}
