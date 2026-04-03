package com.employee.loan_system.lacr.service;

import com.employee.loan_system.lacr.audit.LoanClosureEvent;
import com.employee.loan_system.lacr.audit.LoanClosureEventType;
import com.employee.loan_system.lacr.entity.LoanClosureOutboxEvent;
import com.employee.loan_system.lacr.entity.LoanClosureOutboxStatus;
import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import com.employee.loan_system.lacr.repository.LoanClosureOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanClosureOutboxServiceTest {

    @Mock
    private LoanClosureOutboxEventRepository outboxRepository;

    @Mock
    private FailedEventService failedEventService;

    @Mock
    private LoanClosureEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void appendWorkflowEventShouldPersistPendingOutboxRecord() {
        LoanClosureOutboxService service = new LoanClosureOutboxService(outboxRepository, failedEventService, eventPublisher, objectMapper, 3);

        service.appendWorkflowEvent(sampleEvent());

        ArgumentCaptor<LoanClosureOutboxEvent> captor = ArgumentCaptor.forClass(LoanClosureOutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getPublishStatus()).isEqualTo(LoanClosureOutboxStatus.PENDING);
        assertThat(captor.getValue().getEventType()).isEqualTo("CREATED");
    }

    @Test
    void publishPendingBatchShouldMarkEventsPublished() {
        LoanClosureOutboxService service = new LoanClosureOutboxService(outboxRepository, failedEventService, eventPublisher, objectMapper, 3);
        LoanClosureOutboxEvent pending = new LoanClosureOutboxEvent();
        pending.setId(10L);
        pending.setRequestId("REQ-1001");
        pending.setClosureCaseId(1L);
        pending.setLoanAccountNumber("LN-1001");
        pending.setAggregateType("LoanClosureCase");
        pending.setEventType("CREATED");
        pending.setPayloadJson("{\"requestId\":\"REQ-1001\"}");
        pending.setPublishStatus(LoanClosureOutboxStatus.PENDING);
        when(outboxRepository.findClaimableBatch(any(), anyInt())).thenReturn(List.of(pending));
        when(outboxRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any(LoanClosureOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int published = service.publishPendingBatch();

        assertThat(published).isEqualTo(1);
        verify(outboxRepository).saveAll(any());
        verify(outboxRepository).save(any(LoanClosureOutboxEvent.class));
        assertThat(pending.getPublishStatus()).isEqualTo(LoanClosureOutboxStatus.PUBLISHED);
        assertThat(pending.getPublishedAt()).isNotNull();
        assertThat(pending.getProcessingStartedAt()).isNull();
        assertThat(pending.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void recoverStaleProcessingEventsShouldRequeueStaleWork() {
        LoanClosureOutboxService service = new LoanClosureOutboxService(outboxRepository, failedEventService, eventPublisher, objectMapper, 3);
        LoanClosureOutboxEvent stale = new LoanClosureOutboxEvent();
        stale.setId(11L);
        stale.setRequestId("REQ-1002");
        stale.setClosureCaseId(2L);
        stale.setLoanAccountNumber("LN-2002");
        stale.setAggregateType("LoanClosureCase");
        stale.setEventType("CLOSED");
        stale.setPayloadJson("{\"requestId\":\"REQ-1002\"}");
        stale.setPublishStatus(LoanClosureOutboxStatus.PROCESSING);
        stale.setProcessingStartedAt(LocalDateTime.now().minusHours(1));

        when(outboxRepository.findStaleProcessingBatch(any(), anyInt())).thenReturn(List.of(stale));
        when(outboxRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int recovered = service.recoverStaleProcessingEvents();

        assertThat(recovered).isEqualTo(1);
        verify(outboxRepository).saveAll(any());
        assertThat(stale.getPublishStatus()).isEqualTo(LoanClosureOutboxStatus.PENDING);
        assertThat(stale.getProcessingStartedAt()).isNull();
        assertThat(stale.getErrorMessage()).contains("Recovered stale processing");
    }

    private LoanClosureEvent sampleEvent() {
        return new LoanClosureEvent(
                "REQ-1001",
                1L,
                "LN-1001",
                LoanClosureEventType.CREATED,
                null,
                LoanClosureStatus.REQUESTED,
                ReconciliationStatus.PENDING,
                "closureops",
                "Created from operator console",
                LocalDateTime.of(2026, 3, 30, 22, 20),
                null,
                null
        );
    }
}
