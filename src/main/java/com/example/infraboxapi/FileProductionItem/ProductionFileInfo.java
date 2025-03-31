package com.example.infraboxapi;


import com.example.infraboxapi.productionQueueItem.ProductionQueueItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_productionFileInfo")
public class ProductionFileInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;              // Unikalny identyfikator pliku

    private String fileName;      // Nazwa pliku (np. "program1.nc")
    private String fileType;      // Typ pliku (np. "nc", "pdf")

    @Lob                  // Oznacza duże obiekty binarne (Large Object)
    @Column(name = "file_content")
    private byte[] fileContent;   // Zawartość pliku jako dane binarne

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_queue_item_id")
    private ProductionQueueItem productionQueueItem; // Relacja zwrotna
}
