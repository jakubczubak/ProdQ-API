package com.example.infraboxapi.material;

import com.example.infraboxapi.materialPriceHistory.MaterialPriceHistory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.time.ZonedDateTime;
import java.time.ZoneId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_material")
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private BigDecimal pricePerKg;
    private float minQuantity; // Proponowany minimalny stan ponizej ktorego powinno byc powiadomienie
    private float proposedMinQuantity; // Proponowany minimalny stan na podstawie analizy zużycia
    private float quantity; // Rzeczywista ilość na stanie

    private float z;
    private float y;
    private float x;
    private float diameter;

    private float length;
    private float thickness;

    private String name;
    private BigDecimal price;
    private String type;

    private float quantityInTransit;
    private String additionalInfo;

    @Column(name = "updated_on")
    private String updatedOn;


    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "material_id")
    private List<MaterialPriceHistory> materialPriceHistoryList;

    @PreUpdate
    public void preUpdate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        updatedOn = now.format(formatter);

    }

}
