package com.example.infraboxapi.toolGroup;

import com.example.infraboxapi.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ToolGroupDTO {

    private Integer id;
    private String name;
    private String type;
    private String imageURL;
    private List<Tool> tools;

}
