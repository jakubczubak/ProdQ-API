package com.example.infraboxapi.toolGroup;

import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.FileImage.FileImageRepository;
import com.example.infraboxapi.FileImage.FileImageService;
import com.example.infraboxapi.materialGroup.MaterialGroup;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

        FileImage fileImage = fileImageService.createFile(toolGroupDTO.getFile());

        ToolGroup toolGroup = ToolGroup.builder()
                .name(toolGroupDTO.getName())
                .type(toolGroupDTO.getType())
                .fileImage(fileImage)
                .tools(new ArrayList<>())
                .build();

        toolGroupRepository.save(toolGroup);

        notificationService.createAndSendNotification("A new tool group has been added: " + toolGroup.getName(), NotificationDescription.ToolGroupAdded);

    }


    @Transactional
    public void updateToolGroup(ToolGroupDTO toolGroupDTO) throws IOException {

        ToolGroup toolGroup = toolGroupRepository.findById(toolGroupDTO.getId()).orElseThrow(() -> new RuntimeException("Tool Group not found"));

        toolGroup.setName(toolGroupDTO.getName());
        if(toolGroupDTO.getFile() != null) {
            FileImage fileImage = fileImageService.updateFile(toolGroupDTO.getFile(), toolGroup.getFileImage());
            toolGroup.setFileImage(fileImage);
        }


        toolGroupRepository.save(toolGroup);

        notificationService.createAndSendNotification("A tool group has been updated: " + toolGroup.getName(), NotificationDescription.ToolGroupUpdated);

    }


    @Transactional
    public void deleteToolGroup(Integer id) {

        ToolGroup toolGroup = toolGroupRepository.findById(id).orElseThrow(() -> new RuntimeException("Tool Group not found"));

        toolGroupRepository.delete(toolGroup);

        notificationService.createAndSendNotification("A tool group has been deleted: " + toolGroup.getName(), NotificationDescription.ToolGroupDeleted);

    }

    public ToolGroup getToolGroup(Integer id) {

        return toolGroupRepository.findById(id).orElseThrow(() -> new RuntimeException("Tool Group not found"));


    }


    public void deleteFile(Integer id, Integer materialGroupID) {

        ToolGroup toolGroup = toolGroupRepository.findById(materialGroupID).orElseThrow(() -> new RuntimeException("Tool group not found"));

        FileImage fileImage = fileImageRepository.findById(id).orElseThrow(() -> new RuntimeException("File not found"));

        toolGroup.setFileImage(null);

        toolGroupRepository.save(toolGroup);

        fileImageRepository.delete(fileImage);
    }
}
