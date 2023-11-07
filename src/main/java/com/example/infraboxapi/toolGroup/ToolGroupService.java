package com.example.infraboxapi.toolGroup;

import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ToolGroupService {

    private final ToolGroupRepository toolGroupRepository;
    private final NotificationService notificationService;


    public List<ToolGroup> getToolGroups() {

        return toolGroupRepository.findAll();

    }


    @Transactional
    public void createToolGroup(ToolGroupDTO toolGroupDTO) {

        ToolGroup toolGroup = ToolGroup.builder()
                .name(toolGroupDTO.getName())
                .type(toolGroupDTO.getType())
                .imageURL(toolGroupDTO.getImageURL())
                .tools(new ArrayList<>())
                .build();

        toolGroupRepository.save(toolGroup);

        notificationService.createAndSendNotification("A new tool group has been added: " + toolGroup.getName(), NotificationDescription.ToolGroupAdded);

    }


    @Transactional
    public void updateToolGroup(ToolGroupDTO toolGroupDTO) {

        ToolGroup toolGroup = toolGroupRepository.findById(toolGroupDTO.getId()).orElseThrow(() -> new RuntimeException("Tool Group not found"));

        toolGroup.setName(toolGroupDTO.getName());
        toolGroup.setImageURL(toolGroupDTO.getImageURL());
        toolGroup.setTools(toolGroupDTO.getTools());

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
}
