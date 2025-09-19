package com.example.infraboxapi.orderItem;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {

    // Ta metoda znajdzie wszystkie OrderItem'y, które są powiązane z materiałem o danym ID
    List<OrderItem> findByMaterialId(Integer materialId);

    // DODANA METODA: Ta metoda znajdzie wszystkie OrderItem'y, które są powiązane z narzędziem o danym ID
    List<OrderItem> findByToolId(Integer toolId);
}