package com.example.prodqapi.order;


import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {

    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.supplier " +
           "LEFT JOIN FETCH o.orderItems")
    List<Order> findAllWithSupplierAndItems();

    /**
     * Find all orders for a specific supplier
     */
    List<Order> findBySupplierId(Integer supplierId);

    /**
     * Find completed orders for a supplier (all statuses after full delivery)
     * Includes invoice workflow statuses since delivery has already occurred
     */
    @Query("SELECT o FROM Order o WHERE o.supplier.id = :supplierId " +
           "AND o.status IN ('delivered', 'closed', 'invoice_received', 'closed_short', 'closed_no_invoice', " +
           "'invoice_pending', 'invoice_data_pending', 'invoice_reconciliation')")
    List<Order> findCompletedOrdersBySupplierId(@Param("supplierId") Integer supplierId);

    /**
     * Count total orders for a supplier
     */
    long countBySupplierId(Integer supplierId);

    /**
     * Count completed orders for a supplier (all statuses after full delivery)
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.supplier.id = :supplierId " +
           "AND o.status IN ('delivered', 'closed', 'invoice_received', 'closed_short', 'closed_no_invoice', " +
           "'invoice_pending', 'invoice_data_pending', 'invoice_reconciliation')")
    long countCompletedOrdersBySupplierId(@Param("supplierId") Integer supplierId);

    /**
     * Find order by ID with pessimistic write lock
     * Used to prevent race conditions during invoice reconciliation
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findByIdWithLock(@Param("orderId") Integer orderId);
}
