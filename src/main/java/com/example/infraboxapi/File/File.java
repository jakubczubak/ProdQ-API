package com.example.infraboxapi.File;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
public class File {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Integer id;

    private String name;

    private String type;
    @Lob
    @Column(length = 100000) // Ustaw maksymalną długość kolumny
    private byte[] imageData;
}
