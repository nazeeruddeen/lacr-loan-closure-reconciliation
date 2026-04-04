package com.employee.loan_system.lacr.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lacr.outbox.kafka.consumer.enabled", havingValue = "true")
public class KafkaLoanClosureEventConsumer {

    private final LoanClosureEventConsumptionService consumptionService;

    public KafkaLoanClosureEventConsumer(LoanClosureEventConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @KafkaListener(
            topics = "${lacr.outbox.kafka.topic:loan-closure-events}",
            groupId = "${lacr.outbox.kafka.consumer.group-id:lacr-closure-consumer}",
            containerFactory = "kafkaListenerContainerFactory",
            autoStartup = "${lacr.outbox.kafka.consumer.enabled:false}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        consumptionService.consume(record);
    }

    @KafkaListener(
            topics = "${lacr.outbox.kafka.consumer.dlq-topic:loan-closure-events.DLQ}",
            groupId = "${lacr.outbox.kafka.consumer.group-id:lacr-closure-consumer}.dlq",
            containerFactory = "dlqKafkaListenerContainerFactory",
            autoStartup = "${lacr.outbox.kafka.consumer.enabled:false}"
    )
    public void consumeDeadLetter(ConsumerRecord<String, String> record) {
        consumptionService.captureDeadLetter(record);
    }
}
