package com.example.infraboxapi.material;

import com.example.infraboxapi.materialGroup.MaterialGroup;
import com.example.infraboxapi.materialPriceHistory.MaterialPriceHistory;
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
    private float quantity;

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


    // Explicit getters and setters for transient fields to ensure JSON serialization
    @JsonGetter("reservedQuantity")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Double getReservedQuantity() {
        System.out.println(">>> getReservedQuantity() called, returning: " + reservedQuantity);
        return reservedQuantity;
    }

    @JsonSetter("reservedQuantity")
    public void setReservedQuantity(Double reservedQuantity) {
        System.out.println(">>> setReservedQuantity() called with: " + reservedQuantity);
        this.reservedQuantity = reservedQuantity;
    }

    @JsonGetter("availableQuantity")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Double getAvailableQuantity() {
        System.out.println(">>> getAvailableQuantity() called, returning: " + availableQuantity);
        return availableQuantity;
    }

    @JsonSetter("availableQuantity")
    public void setAvailableQuantity(Double availableQuantity) {
        System.out.println(">>> setAvailableQuantity() called with: " + availableQuantity);
        this.availableQuantity = availableQuantity;
    }

    @PreUpdate
    public void preUpdate() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername = userDetails.getUsername();
        if (!"root@gmail.com".equals(currentUsername)) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            updatedOn = now.format(formatter);
        }
    }
}