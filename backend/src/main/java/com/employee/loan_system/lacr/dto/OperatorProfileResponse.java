package com.employee.loan_system.lacr.dto;

import java.util.List;

public record OperatorProfileResponse(
        String username,
        String displayName,
        List<String> roles
) {
}
