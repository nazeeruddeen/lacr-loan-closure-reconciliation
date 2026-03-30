package com.employee.loan_system.lacr.controller;

import com.employee.loan_system.lacr.config.SecurityConfig;
import com.employee.loan_system.lacr.dto.LoanClosureSummaryResponse;
import com.employee.loan_system.lacr.dto.OperatorProfileResponse;
import com.employee.loan_system.lacr.service.LoanClosureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({LoanClosureController.class, OperatorProfileController.class})
@Import(SecurityConfig.class)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoanClosureService loanClosureService;

    @Test
    void summaryShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/closure-requests/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summaryShouldAllowAuthenticatedOperator() throws Exception {
        when(loanClosureService.summary()).thenReturn(LoanClosureSummaryResponse.builder()
                .totalRequests(8)
                .pendingRequests(2)
                .settlementCalculatedRequests(2)
                .reconciliationPendingRequests(1)
                .reconciledRequests(1)
                .approvedRequests(1)
                .closedRequests(1)
                .rejectedRequests(0)
                .onHoldRequests(0)
                .matchedReconciliations(1)
                .mismatchedReconciliations(0)
                .pendingReconciliations(1)
                .totalSettlementAmount(new BigDecimal("250000.00"))
                .totalOutstandingPrincipal(new BigDecimal("240000.00"))
                .closureStatusCounts(Map.of())
                .reconciliationStatusCounts(Map.of())
                .build());

        mockMvc.perform(get("/api/v1/closure-requests/summary")
                        .with(httpBasic("closureops", "Closure@123"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(8));
    }

    @Test
    void meEndpointShouldReturnCurrentOperator() throws Exception {
        mockMvc.perform(get("/api/v1/operators/me")
                        .with(httpBasic("auditor", "Auditor@123"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("auditor"))
                .andExpect(jsonPath("$.displayName").value("Audit Analyst"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_AUDITOR"));
    }
}
