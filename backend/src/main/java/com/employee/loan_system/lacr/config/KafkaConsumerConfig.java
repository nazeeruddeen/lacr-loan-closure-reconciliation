package com.employee.loan_system.lacr.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "lacr.outbox.kafka.consumer.enabled", havingValue = "true")
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, String> stringKafkaConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${lacr.outbox.kafka.consumer.group-id:lacr-closure-consumer}") String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> stringKafkaConsumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${lacr.outbox.kafka.consumer.dlq-topic:loan-closure-events.DLQ}") String dlqTopic,
            @Value("${lacr.outbox.kafka.consumer.retry-backoff-ms:1000}") long retryBackoffMs,
            @Value("${lacr.outbox.kafka.consumer.max-retries:2}") long maxRetries) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stringKafkaConsumerFactory);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(dlqTopic, record.partition()));
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(retryBackoffMs, maxRetries)));
        return factory;
    }

    @Bean(name = "dlqKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> dlqKafkaListenerContainerFactory(
            ConsumerFactory<String, String> stringKafkaConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stringKafkaConsumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(0L, 0L)));
        return factory;
    }
}
