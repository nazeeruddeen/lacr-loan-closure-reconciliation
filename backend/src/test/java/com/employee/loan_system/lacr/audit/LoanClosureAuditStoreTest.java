package com.employee.loan_system.lacr.audit;

import com.employee.loan_system.lacr.document.LoanClosureAuditEventDocument;
import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import com.employee.loan_system.lacr.repository.LoanClosureAuditEventMongoRepository;
import com.employee.loan_system.lacr.repository.LoanClosureEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanClosureAuditStoreTest {

    @Mock
    private LoanClosureEventRepository eventRepository;

    @Mock
    private LoanClosureAuditEventMongoRepository mongoRepository;

    @Test
    void appendShouldWriteToMongoAndJpaFallback() {
        LoanClosureAuditStore store = new LoanClosureAuditStore(eventRepository, mongoRepository);

        store.append(sampleEvent());

        verify(mongoRepository).save(any(LoanClosureAuditEventDocument.class));
        verify(eventRepository).save(any());
    }

    @Test
    void findAllShouldReadFromMongoFirst() {
        LoanClosureAuditStore store = new LoanClosureAuditStore(eventRepository, mongoRepository);
        when(mongoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(sampleDocument()));

        List<LoanClosureEvent> events = store.findAll();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).requestId()).isEqualTo("REQ-1001");
        verify(eventRepository, never()).findAllByOrderByCreatedAtDesc();
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
                LocalDateTime.of(2026, 3, 30, 22, 5)
        );
    }

    private LoanClosureAuditEventDocument sampleDocument() {
        LoanClosureAuditEventDocument document = new LoanClosureAuditEventDocument();
        document.setRequestId("REQ-1001");
        document.setClosureId(1L);
        document.setLoanAccountNumber("LN-1001");
        document.setEventType(LoanClosureEventType.CREATED);
        document.setToStatus(LoanClosureStatus.REQUESTED);
        document.setReconciliationStatus(ReconciliationStatus.PENDING);
        document.setActor("closureops");
        document.setDetails("Created from operator console");
        document.setCreatedAt(LocalDateTime.of(2026, 3, 30, 22, 5));
        return document;
    }
}
