package com.example.infraboxapi.tool;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_tool")
public class Tool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String type;
    private float dc;
    private float cfl;
    private float oal;
    private float quantity;
    private float minQuantity;
    private BigDecimal price;
    private String toolID;
    private String link;
    private String additionalInfo;
    private float quantityInTransit;

    @Column(name = "updated_on")
    private String updatedOn;


    @PreUpdate
    public void preUpdate() {
        LocalDateTime now = LocalDateTime.now();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        updatedOn = now.format(formatter);
    }
}
