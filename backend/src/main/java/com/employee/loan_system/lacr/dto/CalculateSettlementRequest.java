package com.employee.loan_system.lacr.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CalculateSettlementRequest {

    @NotNull
    @PositiveOrZero
    private BigDecimal adjustmentAmount;

    @Size(max = 1000)
    private String remarks;
}
