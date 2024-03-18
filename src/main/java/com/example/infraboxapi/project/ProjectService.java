package com.example.infraboxapi.project;

import com.example.infraboxapi.departmentCost.DepartmentCost;
import com.example.infraboxapi.departmentCost.DepartmentCostService;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.productionItem.ProductionItem;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@AllArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final NotificationService notificationService;
    private final DepartmentCostService departmentCostService;
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

    public Project getProject(Integer id) {
        return projectRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
    }

    public BigDecimal calculateProductionValueBasedOnDepartmentCost(float productionTime) {
        DepartmentCost departmentCost = departmentCostService.getDepartmentCost();
        BigDecimal departmentMaintenanceCost = BigDecimal.valueOf(departmentCost.getEmployeeCosts() + departmentCost.getMediaPrice() + departmentCost.getDepreciationPrice() + departmentCost.getToolsPrice() + departmentCost.getLeasingPrice() + departmentCost.getVariableCostsI() + departmentCost.getVariableCostsII() + (departmentCost.getPowerConsumption() * departmentCost.getPricePerKwh() * departmentCost.getOperatingHours()) );
        BigDecimal departmentHourlyCost = departmentMaintenanceCost.divide(BigDecimal.valueOf(departmentCost.getOperatingHours()), 2, BigDecimal.ROUND_HALF_UP);
        BigDecimal productionValueBasedOnDepartmentCost = departmentHourlyCost.multiply(BigDecimal.valueOf(productionTime));
        return productionValueBasedOnDepartmentCost;
    }

    public double calculateTotalProductionTime(List<ProductionItem> productionItems) {
        double TotalProductionTime = 0;
        for (ProductionItem productionItem : productionItems) {
            TotalProductionTime += productionItem.getTotalTime();
        }
        return TotalProductionTime;
    }

    public BigDecimal calculateTotalMaterialValue(List<ProductionItem> productionItems) {
        BigDecimal TotalMaterialValue = BigDecimal.valueOf(0);
        for (ProductionItem productionItem : productionItems) {
            TotalMaterialValue = TotalMaterialValue.add(productionItem.getMaterialValue());
        }
        return TotalMaterialValue;
    }

    public BigDecimal calculateTotalToolValue(List<ProductionItem> productionItems) {
        BigDecimal TotalToolValue = BigDecimal.valueOf(0);
        for (ProductionItem productionItem : productionItems) {
            TotalToolValue = TotalToolValue.add(productionItem.getToolValue());
        }
        return TotalToolValue;
    }
}
