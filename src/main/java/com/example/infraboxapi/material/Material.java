package com.example.infraboxapi.material;

import com.example.infraboxapi.materialGroup.MaterialGroup;
import com.example.infraboxapi.materialPriceHistory.MaterialPriceHistory;
import com.example.infraboxapi.supplier.Supplier;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

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
    private float minQuantity;
    private float proposedMinQuantity;

    // Stock fields - different per material type
    private Integer stockQuantity;      // For Plates: number of pieces
    private Float totalStockLength;     // For Rods/Tubes: total length in mm

    // Plate dimensions (mm)
    private float z;  // thickness
    private float y;  // height
    private float x;  // width

    // Rod dimensions (mm)
    private float diameter;

    // Tube dimensions (mm)
    // diameter is reused from Rod (outer diameter)
    private Float innerDiameter;  // inner diameter (replaces thickness!)

    // Rod/Tube length per piece (mm)
    private float length;

    private String name;
    private BigDecimal price;
    private String type;

    @Column(name = "vat_rate")
    @Builder.Default
    private Integer vatRate = 23; // Default VAT rate 23%

    private float quantityInTransit;
    private String additionalInfo;

    @Column(name = "updated_on")
    private String updatedOn;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Double reservedQuantity;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Double availableQuantity;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "material_group_id")
    @ToString.Exclude
    @JsonIgnoreProperties({"materials", "hibernateLazyInitializer", "handler"})
    private MaterialGroup materialGroup;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "material_id")
    @ToString.Exclude // Wyłączenie kolekcji z toString()
    private List<MaterialPriceHistory> materialPriceHistoryList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_supplier_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Supplier preferredSupplier;

    // Explicit getters and setters for transient fields to ensure JSON serialization
    @JsonGetter("reservedQuantity")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Double getReservedQuantity() {
        return reservedQuantity;
    }

    @JsonSetter("reservedQuantity")
    public void setReservedQuantity(Double reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    @JsonGetter("availableQuantity")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Double getAvailableQuantity() {
        return availableQuantity;
    }

    @JsonSetter("availableQuantity")
    public void setAvailableQuantity(Double availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    /**
     * Calculate wall thickness for tubes
     * wallThickness = (outerDiameter - innerDiameter) / 2
     */
    @JsonGetter("wallThickness")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Float getWallThickness() {
        if (diameter > 0 && innerDiameter != null && innerDiameter > 0) {
            return (diameter - innerDiameter) / 2;
        }
        return null;
    }

    @PreUpdate
    public void preUpdate() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername = userDetails.getUsername();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        updatedOn = now.format(formatter);
    }
}