package com.example.infraboxapi.toolGroup;

import com.example.infraboxapi.tool.Tool;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "Field 'name' cannot be blank")
    @Size(min = 2, max = 100, message = "Field 'name' must have a length between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Field 'type' cannot be blank")
    @Size(min = 2, max = 50, message = "Field 'type' must have a length between 2 and 50 characters")
    private String type;

    @NotNull(message = "Field 'imageURL' cannot be null")
    private String imageURL;

    @NotNull(message = "Field 'tools' cannot be null")
    private List<Tool> tools;

}
