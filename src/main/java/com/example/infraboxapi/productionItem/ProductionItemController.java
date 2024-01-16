package com.example.infraboxapi.productionItem;


import com.example.infraboxapi.common.CommonService;
import com.example.infraboxapi.materialGroup.MaterialGroupDTO;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production")
@AllArgsConstructor
public class ProductionItemController {

    private final ProductionItemService productionItemService;
    private final CommonService commonService;

    @PostMapping("/create")
    public ResponseEntity<String> createMaterialGroup(@ModelAttribute @Valid ProductionItemDTO productionItemDTO,
                                                      BindingResult bindingResult){

        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }

        try {
           productionItemService.createProductionItem(productionItemDTO);
            return ResponseEntity.ok("Production item created");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating production item: " + e.getMessage());
        }

    }
}
