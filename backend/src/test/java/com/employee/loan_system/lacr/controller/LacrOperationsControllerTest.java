package com.employee.loan_system.lacr.controller;

import com.employee.loan_system.lacr.config.SecurityConfig;
import com.employee.loan_system.lacr.dto.OutboxHealthResponse;
import com.employee.loan_system.lacr.dto.OutboxRecoveryResponse;
import com.employee.loan_system.lacr.entity.FailedEvent;
import com.employee.loan_system.lacr.service.FailedEventService;
import com.employee.loan_system.lacr.service.LoanClosureOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LacrOperationsController.class)
@Import(SecurityConfig.class)
class LacrOperationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoanClosureOutboxService outboxService;

    @MockBean
    private FailedEventService failedEventService;

    @Test
    void outboxHealthShouldRequireAuth() throws Exception {
        mockMvc.perform(get("/api/v1/ops/outbox/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void outboxHealthShouldReturnLiveHealthSnapshot() throws Exception {
        when(outboxService.outboxHealth()).thenReturn(new OutboxHealthResponse(
                4L,
                2L,
                19L,
                1L,
                1L,
                LocalDateTime.of(2026, 4, 3, 12, 0),
                LocalDateTime.of(2026, 4, 3, 12, 10),
                LocalDateTime.of(2026, 4, 3, 12, 15),
                "PT15M"));

        mockMvc.perform(get("/api/v1/ops/outbox/health")
                        .with(httpBasic("opsadmin", "Ops@123"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(4))
                .andExpect(jsonPath("$.staleProcessingCount").value(1));
    }

    @Test
    void recoverOutboxShouldReturnRecoveryCounts() throws Exception {
        when(outboxService.recoverAndPublishStaleProcessingEvents()).thenReturn(new OutboxRecoveryResponse(2, 2));

        mockMvc.perform(post("/api/v1/ops/outbox/recover")
                        .with(httpBasic("auditor", "Auditor@123"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveredCount").value(2))
                .andExpect(jsonPath("$.republishedCount").value(2));
    }

    @Test
    void failedEventsShouldReturnList() throws Exception {
        FailedEvent failedEvent = new FailedEvent();
        failedEvent.setId(7L);
        failedEvent.setRequestId("REQ-99");
        failedEvent.setLoanAccountNumber("LN-99");
        failedEvent.setFailureReason("Gateway timeout");
        failedEvent.setAttemptCount(3);
        failedEvent.setFailedStage("OUTBOX_PUBLISH");
        failedEvent.setCreatedBy("SYSTEM");
        failedEvent.setFailedAt(LocalDateTime.of(2026, 4, 3, 12, 45));
        when(failedEventService.listAll()).thenReturn(List.of(failedEvent));

        mockMvc.perform(get("/api/v1/ops/failed-events")
                        .with(httpBasic("closureops", "Closure@123"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value("REQ-99"))
                .andExpect(jsonPath("$[0].failedStage").value("OUTBOX_PUBLISH"));
    }
}
