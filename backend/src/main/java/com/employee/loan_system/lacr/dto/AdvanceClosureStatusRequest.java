package com.employee.loan_system.lacr.dto;

import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdvanceClosureStatusRequest {

    @NotNull
    private LoanClosureStatus targetStatus;

    @Size(max = 1000)
    private String remarks;
}
