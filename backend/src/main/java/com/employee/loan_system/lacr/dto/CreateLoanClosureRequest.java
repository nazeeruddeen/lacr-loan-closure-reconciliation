package com.employee.loan_system.lacr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateLoanClosureRequest {

    @NotBlank
    @Size(max = 64)
    private String requestId;

    @NotBlank
    @Size(max = 40)
    private String loanAccountNumber;

    @NotBlank
    @Size(max = 150)
    private String borrowerName;

    @NotBlank
    @Size(max = 200)
    private String closureReason;

    @NotNull
    @PositiveOrZero
    private BigDecimal outstandingPrincipal;

    @NotNull
    @PositiveOrZero
    private BigDecimal accruedInterest;

    @NotNull
    @PositiveOrZero
    private BigDecimal penaltyAmount;

    @NotNull
    @PositiveOrZero
    private BigDecimal processingFee;

    @Size(max = 1000)
    private String remarks;
}
