package com.example.prodqapi.accessorieItem;

import com.example.prodqapi.material.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface AccessorieItemRepository extends JpaRepository<AccessorieItem, Integer> {
    @Query("SELECT a FROM AccessorieItem a WHERE a.quantity < a.minQuantity")
    List<AccessorieItem> findByQuantityLessThanMinQuantity();
}
