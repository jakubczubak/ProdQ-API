package com.example.infraboxapi.orderChangeLog;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OrderChangeLog Entity
 *
 * Stores audit log of all changes made to orders and order items.
 * Tracks changes in: quantity, prices, VAT rates, discounts, and item additions/removals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_order_change_log")
public class OrderChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    /**
     * Type of change:
     * - item_added
     * - item_removed
     * - price_changed
     * - price_per_kg_changed
     * - quantity_changed
     * - vat_changed
     * - discount_changed
     */
    @Column(length = 50)
    private String type;

    @Column(name = "item_name")
    private String itemName;

    /**
     * Field that was changed (e.g., "unitPrice", "quantity", "vatRate")
     */
    @Column(length = 50)
    private String field;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /**
     * Human-readable description of the change
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Date when the change was made (formatted as yyyy-MM-dd HH:mm)
     */
    @Column(name = "change_date", length = 50)
    private String date;
}
