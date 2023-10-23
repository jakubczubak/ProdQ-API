package com.example.infraboxapi.materialGroup;

import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.materialDescription.MaterialDescription;
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
@Table(name = "_material_group")
public class MaterialGroup {

    @Id
    @GeneratedValue
    private Integer id;
    private String name;
    private String type;
    private String imageURL;

    @ManyToOne(cascade = CascadeType.ALL)
    private MaterialDescription materialDescription;



    @OneToMany(mappedBy = "materialGroup", cascade = CascadeType.ALL)
    private List<Material> materials;


}
