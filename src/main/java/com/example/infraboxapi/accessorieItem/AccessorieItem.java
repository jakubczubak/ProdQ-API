package com.example.infraboxapi.accessorieItem;

import com.example.infraboxapi.supplier.Supplier;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_accessorie_item")
public class AccessorieItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String type;
    private float quantity;
    private float minQuantity;
    private BigDecimal price;

    @Column(name = "vat_rate")
    @Builder.Default
    private Integer vatRate = 23; // Default VAT rate 23%

    private String link;
    private String additionalInfo;
    private float length;
    private float diameter;
    private float quantityInTransit;

    @Column(name = "updated_on")
    private String updatedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_supplier_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Supplier preferredSupplier;

    @PreUpdate
    public void preUpdate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw")); // Określ strefę czasową
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        updatedOn = now.format(formatter);
    }
}
