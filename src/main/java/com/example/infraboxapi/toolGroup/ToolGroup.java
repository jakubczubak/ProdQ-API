package com.example.infraboxapi.toolGroup;

import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.tool.Tool;
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
@Table(name="_tool_group")
public class ToolGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String type;
    private String imageURL;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "tool_group_id")
    private List<Tool> tools;


}
