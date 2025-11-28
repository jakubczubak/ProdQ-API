package com.example.prodqapi.toolGroup;

import com.example.prodqapi.FileImage.FileImage;
import com.example.prodqapi.tool.Tool;
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
@Table(name = "_tool_group")
public class ToolGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String type;
    @OneToOne(cascade = CascadeType.ALL)
    private FileImage fileImage;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "tool_group_id")
    private List<Tool> tools;


}
