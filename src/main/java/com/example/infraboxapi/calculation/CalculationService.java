package com.example.infraboxapi.calculation;


import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CalculationService {

    private final CalculationRepository calculationRepository;
    private final NotificationService notificationService;

    public List<Calculation> getAllCalculations() {
        return calculationRepository.findAll();
    }


    @Transactional
    public void addCalculation(CalculationDTO calculationDTO) {

        Calculation calculation = Calculation.builder().shiftLength(calculationDTO.getShiftLength()).numberOfMachines(calculationDTO.getNumberOfMachines()).hourlyRate(calculationDTO.getHourlyRate()).income(calculationDTO.getIncome()).toolCost(calculationDTO.getToolCost()).materialCost(calculationDTO.getMaterialCost()).factor(calculationDTO.getFactor()).camTime(calculationDTO.getCamTime()).variableCostsII(calculationDTO.getVariableCostsII()).variableCostsI(calculationDTO.getVariableCostsI()).leasingPrice(calculationDTO.getLeasingPrice()).toolsPrice(calculationDTO.getToolsPrice()).depreciationPrice(calculationDTO.getDepreciationPrice()).mediaPrice(calculationDTO.getMediaPrice()).pricePerKwh(calculationDTO.getPricePerKwh()).operatingHours(calculationDTO.getOperatingHours()).powerConsumption(calculationDTO.getPowerConsumption()).employeeCosts(calculationDTO.getEmployeeCosts()).status(calculationDTO.getStatus()).selectedDate(calculationDTO.getSelectedDate()).calculationName(calculationDTO.getCalculationName()).billingPeriod(calculationDTO.getBillingPeriod()).startupFee(calculationDTO.getStartupFee()).cncOrderValuation(calculationDTO.getCncOrderValuation()).build();

        calculationRepository.save(calculation);
        notificationService.createAndSendNotification("Calculation " + calculation.getCalculationName() + " has been added successfully.", NotificationDescription.CalculationAdded);
    }

    @Transactional
    public void updateCalculation(CalculationDTO calculationDTO) {

        Calculation calculation = calculationRepository.findById(calculationDTO.getId()).orElseThrow(() -> new RuntimeException("Calculation not found"));

        calculation.setShiftLength(calculationDTO.getShiftLength());
        calculation.setNumberOfMachines(calculationDTO.getNumberOfMachines());
        calculation.setHourlyRate(calculationDTO.getHourlyRate());
        calculation.setIncome(calculationDTO.getIncome());
        calculation.setToolCost(calculationDTO.getToolCost());
        calculation.setMaterialCost(calculationDTO.getMaterialCost());
        calculation.setFactor(calculationDTO.getFactor());
        calculation.setCamTime(calculationDTO.getCamTime());
        calculation.setVariableCostsII(calculationDTO.getVariableCostsII());
        calculation.setVariableCostsI(calculationDTO.getVariableCostsI());
        calculation.setLeasingPrice(calculationDTO.getLeasingPrice());
        calculation.setToolsPrice(calculationDTO.getToolsPrice());
        calculation.setDepreciationPrice(calculationDTO.getDepreciationPrice());
        calculation.setMediaPrice(calculationDTO.getMediaPrice());
        calculation.setPricePerKwh(calculationDTO.getPricePerKwh());
        calculation.setOperatingHours(calculationDTO.getOperatingHours());
        calculation.setPowerConsumption(calculationDTO.getPowerConsumption());
        calculation.setEmployeeCosts(calculationDTO.getEmployeeCosts());
        calculation.setStatus(calculationDTO.getStatus());
        calculation.setSelectedDate(calculationDTO.getSelectedDate());
        calculation.setCalculationName(calculationDTO.getCalculationName());
        calculation.setBillingPeriod(calculationDTO.getBillingPeriod());
        calculation.setStartupFee(calculationDTO.getStartupFee());

        calculationRepository.save(calculation);

        notificationService.createAndSendNotification("Calculation " + calculation.getCalculationName() + " has been updated successfully.", NotificationDescription.CalculationUpdated);

    }

    @Transactional
    public void deleteCalculation(Integer id) {

        Calculation calculation = calculationRepository.findById(id).orElseThrow(() -> new RuntimeException("Calculation not found"));

        calculationRepository.delete(calculation);

        notificationService.createAndSendNotification("Calculation " + calculation.getCalculationName() + " has been deleted successfully.", NotificationDescription.CalculationDeleted);
    }

  
}
