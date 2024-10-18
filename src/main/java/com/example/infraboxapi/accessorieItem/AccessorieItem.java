package com.example.infraboxapi.accessorieItem;


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
    private String link;
    private String additionalInfo;
    private float length;
    private float diameter;

    @Column(name = "updated_on")
    private String updatedOn;


    @PreUpdate
    public void preUpdate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw")); // Określ strefę czasową
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        updatedOn = now.format(formatter);
    }
}
