package com.example.infraboxapi.project;

import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final NotificationService notificationService;
    public void createProject(ProjectDTO projectDTO) {
        Project project = Project.builder()
                .name(projectDTO.getName())
                .status("pending")
                .build();
        projectRepository.save(project);
        notificationService.createAndSendNotification("A new project has been added: " + project.getName(), NotificationDescription.ProjectAdded);
    }

    public Iterable<Project> getProjects() { return projectRepository.findAll(); }

    public void deleteProject(Integer id) {
        Project pr = projectRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        projectRepository.deleteById(id);
        notificationService.createAndSendNotification("A project has been deleted: " + pr.getName() , NotificationDescription.ProjectDeleted);
    }

    public void updateProject(ProjectDTO projectDTO) {
        Project project = projectRepository.findById(projectDTO.getId()).orElseThrow(() -> new RuntimeException("Project not found"));
        project.setName(projectDTO.getName());
        project.setStatus(projectDTO.getStatus());
        projectRepository.save(project);
        notificationService.createAndSendNotification("A project has been updated: " + project.getName() , NotificationDescription.ProjectUpdated);
    }
}
