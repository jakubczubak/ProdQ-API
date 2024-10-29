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
    private float minQuantity; // Rzeczywista minimalna ilość na stanie
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

    private float previousQuantity; // Przechowuje poprzednią ilość przed aktualizacją
    private int additionFrequency; // Częstotliwość dodawania materiału
    private int removalFrequency; // Częstotliwość zdejmowania materiału
    private int usageFrequency; // Ogólna liczba użyć materiału
    private ZonedDateTime lastUsedDate; // Data ostatniego użycia materiału
    private float averageMonthlyUsage; // Średnie miesięczne zużycie materiału

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "material_id")
    private List<MaterialPriceHistory> materialPriceHistoryList;

    @PreUpdate
    public void preUpdate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        updatedOn = now.format(formatter);

        // Sprawdzenie, czy `quantity` uległo zmianie
        if (this.quantity != this.previousQuantity) {
            updateUsageStatistics();
            this.previousQuantity = this.quantity; // Aktualizacja `previousQuantity` do obecnej wartości `quantity`
        }
    }

    private void updateUsageStatistics() {
        if (this.quantity > this.previousQuantity) {
            additionFrequency++;
        } else if (this.quantity < this.previousQuantity) {
            removalFrequency++;
        }

        usageFrequency++;
        lastUsedDate = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));

        // Obliczenie średniego miesięcznego zużycia
        this.averageMonthlyUsage = (removalFrequency > 0) ?
                (this.minQuantity * removalFrequency) / 12 : 0;

        // Aktualizacja proponowanej minimalnej ilości na stanie
        updateProposedMinQuantity();
    }

    private void updateProposedMinQuantity() {
        if (averageMonthlyUsage > 0) {
            // Ustalamy proponowany minimalny stan na podstawie średniego miesięcznego zużycia
            this.proposedMinQuantity = averageMonthlyUsage * 1.2f; // Przykład: 20% zapasu bezpieczeństwa
        }
    }
}
