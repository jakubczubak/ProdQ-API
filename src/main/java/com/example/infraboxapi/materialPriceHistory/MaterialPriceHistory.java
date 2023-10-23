package com.example.infraboxapi.materialPriceHistory;

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
@Table(name = "_material_price_history")
public class MaterialPriceHistory {

    @Id
    @GeneratedValue
    private Integer id;
    private BigDecimal price;
    private String date;


    @PrePersist
    public void preUpdate() {
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        date = currentDateTime.format(formatter);
    }

}
