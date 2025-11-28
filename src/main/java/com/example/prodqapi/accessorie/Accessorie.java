package com.example.prodqapi.accessorie;


import com.example.prodqapi.FileImage.FileImage;
import com.example.prodqapi.accessorieItem.AccessorieItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_accessorie")
public class Accessorie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    @OneToOne(cascade = CascadeType.ALL)
    private FileImage fileImage;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "accessorie_item_id")
    private List<AccessorieItem> accessorieItems;
}
