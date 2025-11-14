package com.example.infraboxapi.order;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.supplier LEFT JOIN FETCH o.orderItems")
    List<Order> findAllWithSupplierAndItems();
}
