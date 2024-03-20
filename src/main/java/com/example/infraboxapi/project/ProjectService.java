package com.example.infraboxapi.project;

import com.example.infraboxapi.departmentCost.DepartmentCost;
import com.example.infraboxapi.departmentCost.DepartmentCostService;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.productionItem.ProductionItem;
import com.example.infraboxapi.productionItem.ProductionItemDTO;
import com.example.infraboxapi.productionItem.ProductionItemService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
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
                .hourlyRate(200)
                .productionItems(new ArrayList<>())
                .productionTime(0)
                .materialValue(BigDecimal.valueOf(0))
                .toolValue(BigDecimal.valueOf(0))
                .productionValue(BigDecimal.valueOf(0))
                .productionValueBasedOnDepartmentCost(BigDecimal.valueOf(0))
                .totalProductionValue(BigDecimal.valueOf(0))
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
        Project project = projectRepository.findById(projectDTO.getId())
                .orElseThrow(() -> new RuntimeException("Project not found"));
        project.setName(projectDTO.getName());
        projectRepository.save(project);
        notificationService.createAndSendNotification("A project has been updated: " + project.getName(), NotificationDescription.ProjectUpdated);
    }

    public Project getProject(Integer id) {
        updateProjectValues(id);
        return projectRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
    }
    public void updateProjectStatus(Integer id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        if (project.getStatus().equals("done")) {
            project.setStatus("pending");
        } else {
            project.setStatus("done");
        }
        projectRepository.save(project);
        notificationService.createAndSendNotification("Project status has been updated: " + project.getName(), NotificationDescription.ProjectStatusUpdated);
    }

    public void updateHourlyRate(Integer id, double hourlyRate) {
        Project project = projectRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        project.setHourlyRate(hourlyRate);
        projectRepository.save(project);
    }

    public BigDecimal calculateProductionValueBasedOnDepartmentCost(float productionTime) {
        DepartmentCost departmentCost = departmentCostService.getDepartmentCost();
        BigDecimal departmentMaintenanceCost = BigDecimal.valueOf(departmentCost.getEmployeeCosts() +
                departmentCost.getMediaPrice() +
                departmentCost.getDepreciationPrice() +
                departmentCost.getToolsPrice() +
                departmentCost.getLeasingPrice() +
                departmentCost.getVariableCostsI() +
                departmentCost.getVariableCostsII() +
                (departmentCost.getPowerConsumption() * departmentCost.getPricePerKwh() * departmentCost.getOperatingHours()));

        BigDecimal departmentHourlyCost = departmentMaintenanceCost.divide(BigDecimal.valueOf(departmentCost.getOperatingHours()), 2, BigDecimal.ROUND_HALF_UP);

        return departmentHourlyCost.multiply(BigDecimal.valueOf(productionTime)).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    public double calculateTotalProductionTime(List<ProductionItem> productionItems) {
        double totalProductionTime = 0;
        for (ProductionItem productionItem : productionItems) {
            totalProductionTime += productionItem.getTotalTime();
        }

        double totalProductionTimeInHours = totalProductionTime / 60;

        // Zaokrąglamy do dwóch miejsc po przecinku
        totalProductionTimeInHours = Math.round(totalProductionTimeInHours * 100.0) / 100.0;

        return totalProductionTimeInHours;
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

    public void updateProjectValues(Integer id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        project.setProductionTime(calculateTotalProductionTime(project.getProductionItems()));
        project.setMaterialValue(calculateTotalMaterialValue(project.getProductionItems()));
        project.setToolValue(calculateTotalToolValue(project.getProductionItems()));
        project.setProductionValue(BigDecimal.valueOf(project.getProductionTime() * project.getHourlyRate()).setScale(2, BigDecimal.ROUND_HALF_UP));
        project.setProductionValueBasedOnDepartmentCost(calculateProductionValueBasedOnDepartmentCost((float) project.getProductionTime()));
        project.setTotalProductionValue(project.getProductionValue().add(project.getMaterialValue()).add(project.getToolValue()).setScale(2, BigDecimal.ROUND_HALF_UP));

        projectRepository.save(project);
    }


    public void addProductionItem(Integer id, ProductionItem productionItem) {
        Project project = projectRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        ProductionItem copyOfProductionItem = ProductionItem.builder()
                .partName(productionItem.getPartName())
                .quantity(productionItem.getQuantity())
                .status(productionItem.getStatus())
                .camTime(productionItem.getCamTime())
                .materialValue(productionItem.getMaterialValue())
                .toolValue(productionItem.getToolValue())
                .partType(productionItem.getPartType())
                .startUpTime(productionItem.getStartUpTime())
                .finishingTime(productionItem.getFinishingTime())
                .totalTime(productionItem.getTotalTime())
                .factor(productionItem.getFactor())
                .fixtureTime(productionItem.getFixtureTime())
                .filePDF(productionItem.getFilePDF())
                .build();
        project.getProductionItems().add(copyOfProductionItem);
        projectRepository.save(project);
    }
}
