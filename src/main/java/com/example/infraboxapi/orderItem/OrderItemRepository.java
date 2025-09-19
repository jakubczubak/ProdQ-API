package com.example.infraboxapi.orderItem;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List; // Dodany import

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {
    // Dodana metoda do wyszukiwania pozycji zamówień po ID materiału
    List<OrderItem> findByMaterialId(Integer materialId);
}