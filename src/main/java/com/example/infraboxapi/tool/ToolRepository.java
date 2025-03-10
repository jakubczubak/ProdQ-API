package com.example.infraboxapi.tool;



import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolRepository extends JpaRepository<Tool, Integer> {
    @Query("SELECT t FROM Tool t WHERE t.quantity < t.minQuantity")
    List<Tool> findByQuantityLessThanMinQuantity();
}
