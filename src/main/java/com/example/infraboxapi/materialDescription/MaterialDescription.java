package com.example.infraboxapi.materialDescription;

import com.example.infraboxapi.materialGroup.MaterialGroup;
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
@Table(name = "_material_description")
public class MaterialDescription {

    @Id
    @GeneratedValue
    private Integer id;
    private String name;
    private float density;

}
