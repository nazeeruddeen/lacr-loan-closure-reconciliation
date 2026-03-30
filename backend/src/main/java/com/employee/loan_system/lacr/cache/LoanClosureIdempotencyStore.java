package com.employee.loan_system.lacr.cache;

import com.employee.loan_system.lacr.dto.LoanClosureResponse;
import com.employee.loan_system.lacr.entity.LoanClosureIdempotencyEntry;
import com.employee.loan_system.lacr.repository.LoanClosureIdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoanClosureIdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(LoanClosureIdempotencyStore.class);
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private static final String REDIS_KEY_PREFIX = "lacr:idempotency:";

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final LoanClosureIdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public LoanClosureIdempotencyStore() {
        this(null, null, null);
    }

    public LoanClosureIdempotencyStore(
            LoanClosureIdempotencyRepository idempotencyRepository,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public Optional<Object> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            if (entry.expiresAt().isBefore(Instant.now())) {
                cache.remove(key);
            } else {
                return Optional.of(entry.value());
            }
        }

        Optional<Object> redisValue = getFromRedis(key);
        if (redisValue.isPresent()) {
            cache.put(key, new CacheEntry(redisValue.get(), Instant.now().plus(DEFAULT_TTL)));
            return redisValue;
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
        putInRedis(key, value);
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

    private Optional<Object> getFromRedis(String key) {
        if (redisTemplate == null) {
            return Optional.empty();
        }

        try {
            ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
            String cachedJson = valueOperations.get(redisKey(key));
            if (cachedJson == null || cachedJson.isBlank()) {
                return Optional.empty();
            }
            RedisIdempotencyValue value = mapper().readValue(cachedJson, RedisIdempotencyValue.class);
            return Optional.ofNullable(deserialize(value.responseType(), value.payloadJson()));
        } catch (Exception ex) {
            log.warn("Redis idempotency read failed for key {}. Falling back to persistent store.", key, ex);
            return Optional.empty();
        }
    }

    private void putInRedis(String key, Object value) {
        if (redisTemplate == null) {
            return;
        }

        try {
            RedisIdempotencyValue payload = new RedisIdempotencyValue(value.getClass().getName(), serialize(value));
            redisTemplate.opsForValue().set(redisKey(key), mapper().writeValueAsString(payload), DEFAULT_TTL);
        } catch (Exception ex) {
            log.warn("Redis idempotency write failed for key {}. Continuing with durable fallback.", key, ex);
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

    private String redisKey(String key) {
        return REDIS_KEY_PREFIX + key;
    }

    private record CacheEntry(Object value, Instant expiresAt) {
    }

    private record RedisIdempotencyValue(String responseType, String payloadJson) {
    }
}
