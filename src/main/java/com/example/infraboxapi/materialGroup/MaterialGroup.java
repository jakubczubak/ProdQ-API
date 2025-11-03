package com.example.infraboxapi.materialGroup;

import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.materialType.MaterialType;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String type;

    @OneToOne(cascade = CascadeType.ALL)
    @JsonIgnore // Don't serialize image in nested contexts
    private FileImage fileImage;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "material_type_id")
    private MaterialType materialType;


    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "material_group_id")
    private List<Material> materials;


}
