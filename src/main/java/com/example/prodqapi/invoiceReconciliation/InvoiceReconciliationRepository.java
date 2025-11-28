package com.example.prodqapi.invoiceReconciliation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceReconciliationRepository extends JpaRepository<InvoiceReconciliation, Integer> {

    /**
     * Find reconciliation by order ID
     */
    Optional<InvoiceReconciliation> findByOrderId(Integer orderId);

    /**
     * Check if reconciliation exists for order
     */
    boolean existsByOrderId(Integer orderId);
}
