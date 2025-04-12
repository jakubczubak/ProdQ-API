package com.example.infraboxapi.productionQueueItem;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MachineRequest {
    @NotBlank(message = "Machine name cannot be blank")
    private String machineName;

    @NotBlank(message = "Program path cannot be blank")
    private String programPath;

    @NotBlank(message = "Queue file path cannot be blank")
    private String queueFilePath;
}