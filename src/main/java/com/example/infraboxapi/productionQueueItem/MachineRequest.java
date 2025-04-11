package com.example.infraboxapi.productionQueueItem;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MachineRequest {
    @NotBlank(message = "Machine name cannot be blank")
    private String machineName;

    private String programPath;

    private String queueFilePath;
}