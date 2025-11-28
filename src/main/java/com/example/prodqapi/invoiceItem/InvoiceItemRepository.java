package com.example.prodqapi.invoiceItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Integer> {

    /**
     * Find all invoice items for a specific order
     */
    List<InvoiceItem> findByOrderId(Integer orderId);

    /**
     * Delete all invoice items for a specific order
     */
    @Modifying
    @Query("DELETE FROM InvoiceItem ii WHERE ii.order.id = :orderId")
    void deleteByOrderId(@Param("orderId") Integer orderId);

    /**
     * Find invoice item by order item ID
     */
    InvoiceItem findByOrderItemId(Integer orderItemId);
}
