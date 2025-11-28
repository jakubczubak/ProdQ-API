package com.example.prodqapi.toolGroup;

import com.example.prodqapi.FileImage.FileImage;
import com.example.prodqapi.FileImage.FileImageRepository;
import com.example.prodqapi.FileImage.FileImageService;
import com.example.prodqapi.notification.NotificationDescription;
import com.example.prodqapi.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class ToolGroupService {

    private final ToolGroupRepository toolGroupRepository;
    private final NotificationService notificationService;
    private final FileImageService fileImageService;
    private final FileImageRepository fileImageRepository;


    public List<ToolGroup> getToolGroups() {

        return toolGroupRepository.findAll();

    }


    @Transactional
    public void createToolGroup(ToolGroupDTO toolGroupDTO) throws IOException {

        ToolGroup toolGroup = ToolGroup.builder()
                .name(toolGroupDTO.getName())
                .type(toolGroupDTO.getType())
                .tools(new ArrayList<>())
                .build();

        if (toolGroupDTO.getFile() != null) {
            FileImage fileImage = fileImageService.createFile(toolGroupDTO.getFile());
            toolGroup.setFileImage(fileImage);
        }

        toolGroupRepository.save(toolGroup);

        notificationService.sendNotification(NotificationDescription.ToolGroupAdded, Map.of("name", toolGroup.getName()));

    }


    @Transactional
    public void updateToolGroup(ToolGroupDTO toolGroupDTO) throws IOException {

        ToolGroup toolGroup = toolGroupRepository.findById(toolGroupDTO.getId()).orElseThrow(() -> new RuntimeException("Tool Group not found"));

        toolGroup.setName(toolGroupDTO.getName());
        if (toolGroupDTO.getFile() != null) {
            FileImage fileImage = fileImageService.updateFile(toolGroupDTO.getFile(), toolGroup.getFileImage());
            toolGroup.setFileImage(fileImage);
        }


        toolGroupRepository.save(toolGroup);

        notificationService.sendNotification(NotificationDescription.ToolGroupUpdated, Map.of("name", toolGroup.getName()));

    }


    @Transactional
    public void deleteToolGroup(Integer id) {

        ToolGroup toolGroup = toolGroupRepository.findById(id).orElseThrow(() -> new RuntimeException("Tool Group not found"));

        toolGroupRepository.delete(toolGroup);

        notificationService.sendNotification(NotificationDescription.ToolGroupDeleted, Map.of("name", toolGroup.getName()));

    }

    public ToolGroup getToolGroup(Integer id) {

        return toolGroupRepository.findById(id).orElseThrow(() -> new RuntimeException("Tool Group not found"));


    }


    public void deleteFile(Integer id, Integer materialGroupID) {

        ToolGroup toolGroup = toolGroupRepository.findById(materialGroupID).orElseThrow(() -> new RuntimeException("Tool group not found"));

        FileImage fileImage = fileImageRepository.findById(Long.valueOf(id)).orElseThrow(() -> new RuntimeException("File not found"));

        toolGroup.setFileImage(null);

        toolGroupRepository.save(toolGroup);

        fileImageRepository.delete(fileImage);
    }
}
