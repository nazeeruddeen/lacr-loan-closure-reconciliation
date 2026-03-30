package com.employee.loan_system.lacr.cache;

import com.employee.loan_system.lacr.dto.LoanClosureResponse;
import com.employee.loan_system.lacr.entity.LoanClosureIdempotencyEntry;
import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import com.employee.loan_system.lacr.repository.LoanClosureIdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanClosureIdempotencyStoreTest {

    @Mock
    private LoanClosureIdempotencyRepository idempotencyRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void putShouldWriteToRedisAndRepository() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(idempotencyRepository.findByIdempotencyKey("CREATE:REQ-1")).thenReturn(Optional.empty());

        LoanClosureIdempotencyStore store = new LoanClosureIdempotencyStore(idempotencyRepository, objectMapper, redisTemplate);
        LoanClosureResponse response = sampleResponse();

        store.put("CREATE:REQ-1", response);

        verify(valueOperations).set(eq("lacr:idempotency:CREATE:REQ-1"), any(String.class), any(Duration.class));
        verify(idempotencyRepository).save(any(LoanClosureIdempotencyEntry.class));
    }

    @Test
    void getShouldPreferRedisBeforeRepository() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        LoanClosureResponse response = sampleResponse();
        String payloadJson = objectMapper.writeValueAsString(response);
        String redisEnvelope = objectMapper.writeValueAsString(
                new RedisEnvelope(LoanClosureResponse.class.getName(), payloadJson));
        when(valueOperations.get("lacr:idempotency:CREATE:REQ-1")).thenReturn(redisEnvelope);

        LoanClosureIdempotencyStore store = new LoanClosureIdempotencyStore(idempotencyRepository, objectMapper, redisTemplate);

        Optional<Object> restored = store.get("CREATE:REQ-1");

        assertThat(restored).isPresent();
        assertThat(restored.get()).isInstanceOf(LoanClosureResponse.class);
        assertThat(((LoanClosureResponse) restored.get()).requestId()).isEqualTo("REQ-1");
        verify(idempotencyRepository, never()).findByIdempotencyKey(any());
    }

    private LoanClosureResponse sampleResponse() {
        return LoanClosureResponse.builder()
                .id(1L)
                .requestId("REQ-1")
                .loanAccountNumber("LN-1")
                .borrowerName("Asha Rao")
                .closureReason("Pre-closure")
                .outstandingPrincipal(new BigDecimal("100000.00"))
                .accruedInterest(new BigDecimal("1200.00"))
                .penaltyAmount(new BigDecimal("300.00"))
                .processingFee(new BigDecimal("100.00"))
                .settlementAdjustment(BigDecimal.ZERO.setScale(2))
                .settlementAmount(new BigDecimal("101600.00"))
                .closureStatus(LoanClosureStatus.REQUESTED)
                .reconciliationStatus(ReconciliationStatus.PENDING)
                .createdBy("closureops")
                .statusHistory(List.of())
                .build();
    }

    private record RedisEnvelope(String responseType, String payloadJson) {
    }
}
