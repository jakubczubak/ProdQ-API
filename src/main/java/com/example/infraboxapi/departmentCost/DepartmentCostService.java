package com.example.infraboxapi.departmentCost;


import com.example.infraboxapi.user.UserService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class DepartmentCostService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final DepartmentCostRepository departmentCostRepository;


    public void createDefaultDepartmentCost() {
        DepartmentCost departmentCost = DepartmentCost.builder()
                .billingPeriod(200)
                .employeeCosts(5000)
                .powerConsumption(30)
                .operatingHours(200)
                .pricePerKwh(0.79)
                .mediaPrice(1000)
                .depreciationPrice(0)
                .toolsPrice(0)
                .leasingPrice(0)
                .variableCostsI(0)
                .variableCostsII(0)
                .build();
        departmentCostRepository.save(departmentCost);
        logger.info("Default department cost created successfully :)");
    }

    public DepartmentCost getDepartmentCost() {

        return departmentCostRepository.findById(1).orElseThrow(() -> new RuntimeException("Department cost not found"));
    }

    public void updateDepartmentCost(DepartmentCostDTO departmentCostDTO) {

        DepartmentCost departmentCost = departmentCostRepository.findById(1).orElseThrow(() -> new RuntimeException("Department cost not found"));

        departmentCost.setBillingPeriod(departmentCostDTO.getBillingPeriod());
        departmentCost.setEmployeeCosts(departmentCostDTO.getEmployeeCosts());
        departmentCost.setPowerConsumption(departmentCostDTO.getPowerConsumption());
        departmentCost.setOperatingHours(departmentCostDTO.getOperatingHours());
        departmentCost.setPricePerKwh(departmentCostDTO.getPricePerKwh());
        departmentCost.setMediaPrice(departmentCostDTO.getMediaPrice());
        departmentCost.setDepreciationPrice(departmentCostDTO.getDepreciationPrice());
        departmentCost.setToolsPrice(departmentCostDTO.getToolsPrice());
        departmentCost.setLeasingPrice(departmentCostDTO.getLeasingPrice());
        departmentCost.setVariableCostsI(departmentCostDTO.getVariableCostsI());
        departmentCost.setVariableCostsII(departmentCostDTO.getVariableCostsII());

        departmentCostRepository.save(departmentCost);
        logger.info("Department cost updated successfully :)");
    }
}
