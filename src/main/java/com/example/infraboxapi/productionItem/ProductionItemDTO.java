package com.example.infraboxapi.productionItem;

import com.example.infraboxapi.FilePDF.FilePDF;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ProductionItemDTO {
    private Integer id;
    @NotBlank(message = "Field 'part name' cannot be blank")
    @Size(min = 2, max = 100, message = "Field 'part name' must have a length between 2 and 100 characters")
    private String partName;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer quantity;
    @NotBlank(message = "Field 'status' cannot be blank")
    @Size(min = 2, max = 100, message = "Field 'status' must have a length between 2 and 100 characters")
    private String status;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double camTime;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double startUpTime;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double finishingTime;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double totalTime;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double fixtureTime;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double factor;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private BigDecimal materialValue;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private BigDecimal toolValue;
    @NotBlank(message = "Field 'part type' cannot be blank")
    @Size(min = 2, max = 100, message = "Field 'part type' must have a length between 2 and 100 characters")
    private String partType;
    private MultipartFile filePDF;
}
