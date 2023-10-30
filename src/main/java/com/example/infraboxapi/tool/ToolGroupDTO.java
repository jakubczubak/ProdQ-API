package com.example.infraboxapi.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ToolGroupDTO {

        private Integer id;
    private String name;
    private String type;
    private String imageURL;

}
