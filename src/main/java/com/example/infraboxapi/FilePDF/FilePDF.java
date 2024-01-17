package com.example.infraboxapi.FilePDF;


import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Table(name = "_file_pdf")
public class FilePDF {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Integer id;

    private String name;

    private String type;
    @Lob
    @Column(length = 100000) // Ustaw maksymalną długość kolumny
    private byte[] pdfData;
}
