package com.example.infraboxapi.toolGroup;

import com.example.infraboxapi.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ToolGroupDTO {

    @NotBlank(message = "Field 'name' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'name' must have a length between 1 and 100 characters")
    private String name;

    @NotBlank(message = "Field 'type' cannot be blank")
    @Size(min = 1, max = 50, message = "Field 'type' must have a length between 1 and 50 characters")
    private String type;

    @NotBlank(message = "Field 'imageURL' cannot be blank")
    @Pattern(regexp = "^https?://.*$", message = "Field 'imageURL' must be a valid URL starting with http:// or https://")
    private String imageURL;

    @NotNull(message = "Field 'tools' cannot be null")
    private List<Tool> tools;

}
