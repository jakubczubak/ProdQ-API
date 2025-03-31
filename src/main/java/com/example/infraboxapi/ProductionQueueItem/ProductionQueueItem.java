package com.example.infraboxapi.ProductionQueueItemService;

import com.example.infraboxapi.FileProductionItem.ProductionFileInfo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_productionQueueItem")
public class ProductionQueueItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;              // Unikalny identyfikator
    private String partName;        // Nazwa części, np. "11_06_LSDAL7525"
    private String orderName;       // Nazwa zamówienia, np. "1077_LS_SAL"
    private int quantity;           // Ilość, np. 1
    private String type;            // Typ, np. "Mill"
    private String subtype;         // Podtyp, np. "Plate"
    private String baseCamTime;     // Bazowy czas CAM, np. "03:25:31"
    private String camTime;         // Czas CAM, np. "03:25:31"
    private String deadline;        // Termin (może być null), np. "2025-03-02"
    private String additionalInfo;  // Dodatkowe informacje, np. ""
    private String fileDirectory;   // Ścieżka do katalogu plików, np. "\\172.16.2.14\..."

    @OneToMany(mappedBy = "productionQueueItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductionFileInfo> files = new ArrayList<>(); // Lista informacji o plikach

    private String author;          // Autor, np. "Jan Kowalski"
    private boolean isCompleted;    // Czy zakończone, domyślnie false
}