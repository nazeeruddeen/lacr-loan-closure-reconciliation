package com.employee.loan_system.lacr.service;

import com.employee.loan_system.lacr.audit.LoanClosureEvent;
import com.employee.loan_system.lacr.entity.FailedEvent;
import com.employee.loan_system.lacr.entity.LoanClosureConsumedEvent;
import com.employee.loan_system.lacr.repository.LoanClosureConsumedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class LoanClosureEventConsumptionService {

    private static final Logger log = LoggerFactory.getLogger(LoanClosureEventConsumptionService.class);

    private final LoanClosureConsumedEventRepository consumedEventRepository;
    private final FailedEventService failedEventService;
    private final ObjectMapper objectMapper;
    private final String consumerGroupId;
    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter dlqCounter;

    public LoanClosureEventConsumptionService(
            LoanClosureConsumedEventRepository consumedEventRepository,
            FailedEventService failedEventService,
            ObjectMapper objectMapper,
            @Value("${lacr.outbox.kafka.consumer.group-id:lacr-closure-consumer}") String consumerGroupId) {
        this(consumedEventRepository, failedEventService, objectMapper, consumerGroupId, null);
    }

    @Autowired
    public LoanClosureEventConsumptionService(
            LoanClosureConsumedEventRepository consumedEventRepository,
            FailedEventService failedEventService,
            ObjectMapper objectMapper,
            @Value("${lacr.outbox.kafka.consumer.group-id:lacr-closure-consumer}") String consumerGroupId,
            MeterRegistry meterRegistry) {
        this.consumedEventRepository = consumedEventRepository;
        this.failedEventService = failedEventService;
        this.objectMapper = objectMapper;
        this.consumerGroupId = consumerGroupId;
        this.processedCounter = meterRegistry == null ? null : meterRegistry.counter("lacr.consumer_events_processed_total");
        this.duplicateCounter = meterRegistry == null ? null : meterRegistry.counter("lacr.consumer_events_duplicate_total");
        this.dlqCounter = meterRegistry == null ? null : meterRegistry.counter("lacr.consumer_events_dlq_total");
    }

    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        String requestId = firstNonBlank(header(record, "requestId"), record.key());
        String idempotencyKey = firstNonBlank(header(record, "idempotencyKey"), requestId);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalStateException("Kafka consumer record is missing idempotencyKey/requestId");
        }

        if (consumedEventRepository.existsByIdempotencyKey(idempotencyKey)) {
            increment(duplicateCounter);
            log.info("LoanClosureEventConsumptionService: duplicate consumer event skipped for idempotencyKey={}", idempotencyKey);
            return;
        }

        LoanClosureEvent event = deserialize(record.value());
        if (requestId != null && event.requestId() != null && !requestId.equals(event.requestId())) {
            throw new IllegalStateException("Kafka consumer requestId header does not match payload requestId");
        }

        LoanClosureConsumedEvent consumedEvent = new LoanClosureConsumedEvent();
        consumedEvent.setIdempotencyKey(idempotencyKey);
        consumedEvent.setRequestId(firstNonBlank(event.requestId(), requestId, idempotencyKey));
        consumedEvent.setClosureCaseId(event.closureId());
        consumedEvent.setLoanAccountNumber(firstNonBlank(event.loanAccountNumber(), header(record, "loanAccountNumber")));
        consumedEvent.setEventType(firstNonBlank(
                event.eventType() == null ? null : event.eventType().name(),
                header(record, "eventType")));
        consumedEvent.setAggregateType(firstNonBlank(header(record, "aggregateType"), "LoanClosureCase"));
        consumedEvent.setTopicName(record.topic());
        consumedEvent.setPartitionNumber(record.partition());
        consumedEvent.setRecordOffset(record.offset());
        consumedEvent.setPayloadHash(sha256(record.value()));

        consumedEventRepository.save(consumedEvent);
        increment(processedCounter);
    }

    public FailedEvent captureDeadLetter(ConsumerRecord<String, String> record) {
        String requestId = firstNonBlank(header(record, "requestId"), record.key(), "UNKNOWN");
        String loanAccountNumber = header(record, "loanAccountNumber");
        String reason = firstNonBlank(
                header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
                "Kafka consumer exhausted retries and routed the record to DLQ");
        int attemptCount = parseInt(header(record, KafkaHeaders.DELIVERY_ATTEMPT), 1);
        increment(dlqCounter);
        return failedEventService.recordFailure(
                requestId,
                loanAccountNumber,
                record.value(),
                reason,
                attemptCount,
                "KAFKA_CONSUMER_DLQ",
                consumerGroupId
        );
    }

    private LoanClosureEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, LoanClosureEvent.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize loan closure event payload", ex);
        }
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
