package com.employee.loan_system.lacr.service;

import com.employee.loan_system.lacr.audit.LoanClosureEvent;
import com.employee.loan_system.lacr.audit.LoanClosureEventType;
import com.employee.loan_system.lacr.entity.FailedEvent;
import com.employee.loan_system.lacr.entity.LoanClosureConsumedEvent;
import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import com.employee.loan_system.lacr.repository.LoanClosureConsumedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanClosureEventConsumptionServiceTest {

    @Mock
    private LoanClosureConsumedEventRepository consumedEventRepository;

    @Mock
    private FailedEventService failedEventService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void consumeShouldPersistFirstSeenEvent() throws Exception {
        LoanClosureEventConsumptionService service = new LoanClosureEventConsumptionService(
                consumedEventRepository,
                failedEventService,
                objectMapper,
                "lacr-closure-consumer");

        LoanClosureEvent event = new LoanClosureEvent(
                "REQ-100",
                12L,
                "LN-100",
                LoanClosureEventType.CREATED,
                null,
                LoanClosureStatus.REQUESTED,
                ReconciliationStatus.PENDING,
                "closureops",
                "created",
                LocalDateTime.of(2026, 4, 4, 10, 30),
                null,
                "hash-1");

        ConsumerRecord<String, String> record = record(
                "loan-closure-events",
                "REQ-100",
                objectMapper.writeValueAsString(event),
                "requestId", "REQ-100",
                "idempotencyKey", "REQ-100",
                "loanAccountNumber", "LN-100",
                "eventType", "CREATED",
                "aggregateType", "LoanClosureCase");

        when(consumedEventRepository.existsByIdempotencyKey("REQ-100")).thenReturn(false);

        service.consume(record);

        ArgumentCaptor<LoanClosureConsumedEvent> captor = ArgumentCaptor.forClass(LoanClosureConsumedEvent.class);
        verify(consumedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("REQ-100");
        assertThat(captor.getValue().getLoanAccountNumber()).isEqualTo("LN-100");
        assertThat(captor.getValue().getTopicName()).isEqualTo("loan-closure-events");
    }

    @Test
    void consumeShouldSkipDuplicateIdempotencyKey() {
        LoanClosureEventConsumptionService service = new LoanClosureEventConsumptionService(
                consumedEventRepository,
                failedEventService,
                objectMapper,
                "lacr-closure-consumer");

        ConsumerRecord<String, String> record = record(
                "loan-closure-events",
                "REQ-100",
                "{}",
                "requestId", "REQ-100",
                "idempotencyKey", "REQ-100");

        when(consumedEventRepository.existsByIdempotencyKey("REQ-100")).thenReturn(true);

        service.consume(record);

        verify(consumedEventRepository, never()).save(any());
    }

    @Test
    void captureDeadLetterShouldPersistFailureRecord() {
        LoanClosureEventConsumptionService service = new LoanClosureEventConsumptionService(
                consumedEventRepository,
                failedEventService,
                objectMapper,
                "lacr-closure-consumer");

        FailedEvent failedEvent = new FailedEvent();
        failedEvent.setId(1L);
        when(failedEventService.recordFailure(
                eq("REQ-200"),
                eq("LN-200"),
                eq("{\"payload\":true}"),
                eq("boom"),
                eq(1),
                eq("KAFKA_CONSUMER_DLQ"),
                eq("lacr-closure-consumer")
        )).thenReturn(failedEvent);

        ConsumerRecord<String, String> record = record(
                "loan-closure-events.DLQ",
                "REQ-200",
                "{\"payload\":true}",
                "requestId", "REQ-200",
                "loanAccountNumber", "LN-200",
                "kafka_dlt-exception-message", "boom");

        FailedEvent response = service.captureDeadLetter(record);

        assertThat(response.getId()).isEqualTo(1L);
        verify(failedEventService).recordFailure(
                "REQ-200",
                "LN-200",
                "{\"payload\":true}",
                "boom",
                1,
                "KAFKA_CONSUMER_DLQ",
                "lacr-closure-consumer"
        );
    }

    private ConsumerRecord<String, String> record(String topic, String key, String value, String... headers) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 15L, key, value);
        for (int index = 0; index < headers.length; index += 2) {
            record.headers().add(headers[index], headers[index + 1].getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
}
