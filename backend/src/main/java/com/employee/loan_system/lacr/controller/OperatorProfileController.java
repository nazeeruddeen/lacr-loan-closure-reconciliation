package com.employee.loan_system.lacr.controller;

import com.employee.loan_system.lacr.dto.OperatorProfileResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/operators")
public class OperatorProfileController {

    @GetMapping("/me")
    public OperatorProfileResponse currentOperator(Authentication authentication) {
        String username = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return new OperatorProfileResponse(
                username,
                displayNameFor(username),
                roles);
    }

    private String displayNameFor(String username) {
        return switch (username) {
            case "closureops" -> "Closure Operations";
            case "reconlead" -> "Reconciliation Lead";
            case "auditor" -> "Audit Analyst";
            case "opsadmin" -> "Operations Admin";
            default -> username;
        };
    }
}
