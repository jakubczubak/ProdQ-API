

package com.example.prodqapi.FileImage;

import lombok.*;

import jakarta.persistence.*;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Table(name = "_fileImage")
public class FileImage {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Integer id;

    private String name;

    private String type;
    @Lob
    @Column(length = 100000) // Ustaw maksymalną długość kolumny
    private byte[] imageData;
}
