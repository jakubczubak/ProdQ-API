package com.example.prodqapi.orderChangeLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * OrderChangeLogRepository
 *
 * Repository for managing order change log entries.
 */
@Repository
public interface OrderChangeLogRepository extends JpaRepository<OrderChangeLog, Integer> {

    /**
     * Find all change log entries for a specific order, ordered by date descending
     *
     * @param orderId The order ID
     * @return List of change log entries
     */
    List<OrderChangeLog> findByOrderIdOrderByDateDesc(Integer orderId);

    /**
     * Delete all change log entries for a specific order
     *
     * @param orderId The order ID
     */
    void deleteByOrderId(Integer orderId);
}
