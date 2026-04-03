package com.employee.loan_system.lacr.service;

import com.employee.loan_system.lacr.entity.LoanClosureOutboxEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "lacr.outbox.publisher.mode", havingValue = "kafka")
public class KafkaLoanClosureEventPublisher implements LoanClosureEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaLoanClosureEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${lacr.outbox.kafka.topic:loan-closure-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(LoanClosureOutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getRequestId(), event.getPayloadJson());
        record.headers().add("requestId", bytes(event.getRequestId()));
        record.headers().add("closureCaseId", bytes(String.valueOf(event.getClosureCaseId())));
        record.headers().add("loanAccountNumber", bytes(event.getLoanAccountNumber()));
        record.headers().add("eventType", bytes(event.getEventType()));
        record.headers().add("aggregateType", bytes(event.getAggregateType()));
        record.headers().add("idempotencyKey", bytes(event.getRequestId()));
        kafkaTemplate.send(record).join();
    }

    private byte[] bytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
