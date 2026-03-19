package com.employee.loan_system.lacr.cache;

import com.employee.loan_system.lacr.dto.LoanClosureResponse;
import com.employee.loan_system.lacr.entity.LoanClosureIdempotencyEntry;
import com.employee.loan_system.lacr.repository.LoanClosureIdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoanClosureIdempotencyStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private LoanClosureIdempotencyRepository idempotencyRepository;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    public Optional<Object> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            if (entry.expiresAt().isBefore(Instant.now())) {
                cache.remove(key);
            } else {
                return Optional.of(entry.value());
            }
        }

        if (idempotencyRepository == null) {
            return Optional.empty();
        }

        Optional<LoanClosureIdempotencyEntry> record = idempotencyRepository.findByIdempotencyKey(key);
        if (record.isEmpty()) {
            return Optional.empty();
        }

        LoanClosureIdempotencyEntry entryRecord = record.get();
        if (entryRecord.getExpiresAt().isBefore(LocalDateTime.now())) {
            idempotencyRepository.delete(entryRecord);
            return Optional.empty();
        }

        Object value = deserialize(entryRecord.getResponseType(), entryRecord.getPayloadJson());
        if (value != null) {
            cache.put(key, new CacheEntry(value, entryRecord.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant()));
        }
        return Optional.ofNullable(value);
    }

    public void put(String key, Object value) {
        CacheEntry cacheEntry = new CacheEntry(value, Instant.now().plus(DEFAULT_TTL));
        cache.put(key, cacheEntry);
        if (idempotencyRepository != null) {
            LoanClosureIdempotencyEntry entry = idempotencyRepository.findByIdempotencyKey(key)
                    .orElseGet(LoanClosureIdempotencyEntry::new);
            entry.setIdempotencyKey(key);
            entry.setResponseType(value.getClass().getName());
            entry.setPayloadJson(serialize(value));
            entry.setExpiresAt(LocalDateTime.now().plus(DEFAULT_TTL));
            idempotencyRepository.save(entry);
        }
    }

    private Object deserialize(String responseType, String payloadJson) {
        try {
            Class<?> type = Class.forName(responseType);
            if (LoanClosureResponse.class.equals(type)) {
                return mapper().readValue(payloadJson, LoanClosureResponse.class);
            }
            return mapper().readValue(payloadJson, type);
        } catch (Exception ex) {
            return null;
        }
    }

    private String serialize(Object value) {
        try {
            return mapper().writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize idempotent response", ex);
        }
    }

    private ObjectMapper mapper() {
        if (objectMapper != null) {
            return objectMapper;
        }
        return new ObjectMapper().findAndRegisterModules();
    }

    private record CacheEntry(Object value, Instant expiresAt) {
    }
}
