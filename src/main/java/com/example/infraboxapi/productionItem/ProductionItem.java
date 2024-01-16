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
    private Integer quantity;
    @Column(name = "updated_on")
    private String updatedOn;
    private String status;
    private double camTime;
    private BigDecimal materialValue;
    private String partType;
    @OneToOne(cascade = CascadeType.ALL)
    private FilePDF filePDF;


    @PreUpdate
    public void preUpdate() {
        LocalDateTime now = LocalDateTime.now();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        updatedOn = now.format(formatter);
    }
}
