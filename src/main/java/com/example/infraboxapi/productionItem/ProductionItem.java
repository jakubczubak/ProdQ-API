package com.example.infraboxapi.productionItem;

import com.example.infraboxapi.FilePDF.FilePDF;
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
@Table(name = "_production_item")
public class ProductionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String partName;
    private float quantity;
    @Column(name = "updated_on")
    private String updatedOn;
    @Column(name = "craeted_on")
    private String createdOn;
    private String status;
    private double camTime;
    private double fixtureTime;
    private double factor;
    private double startUpTime;
    private double finishingTime;
    private double totalTime;
    private BigDecimal materialValue;
    private BigDecimal toolValue;
    private String partType;
    private String typeOfProcessing;
    @OneToOne(cascade = CascadeType.ALL)
    private FilePDF filePDF;


    @PreUpdate
    public void preUpdate() {
        LocalDateTime now = LocalDateTime.now();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        updatedOn = now.format(formatter);
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        createdOn = now.format(formatter);
    }
}
