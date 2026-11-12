package com.zekodnix.zaramoney.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zekodnix.zaramoney.domain.IdempotencyRecord;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.repository.IdempotencyRecordRepository;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency for money-moving endpoints.
 * <ul>
 *   <li>Keys are scoped per user: the stored hash is {@code SHA-256(userId + ":" + key)}, so another user's key never matches.</li>
 *   <li>A key reused with a different payload (or on another endpoint) is rejected with 409.</li>
 *   <li>Two parallel requests with the same key: the second blocks on the unique index of {@code key_hash} until the first
 *   commits, then fails with a 409; it never moves money twice.</li>
 * </ul>
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class IdempotencyRecordServicePlus {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_COMPLETED = 200;

    private static final ObjectMapper JSON = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyRecordServicePlus(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    /**
     * @return the transaction reference of an already-completed request with the same key, if any.
     */
    public Optional<String> findCompleted(String key, User user, String endpoint, String requestHash) {
        return idempotencyRecordRepository
            .findByKeyHash(hashKey(key, user))
            .map(record -> {
                if (!Objects.equals(record.getUserId(), user.getId())) {
                    // cannot happen (user id is part of the hash), but never return someone else's result
                    throw ApiException.conflict("Idempotency key already used", "idempotencykeyreused");
                }
                if (
                    !Objects.equals(record.getEndpoint(), endpoint) ||
                    (record.getRequestHash() != null && !record.getRequestHash().equals(requestHash))
                ) {
                    throw ApiException.conflict(
                        "Idempotency key already used for a different request; generate a new key",
                        "idempotencykeyreused"
                    );
                }
                if (record.getResponseStatus() == null || record.getResponseStatus() != STATUS_COMPLETED) {
                    throw ApiException.conflict("A request with this idempotency key is still being processed", "requestinprogress");
                }
                return record.getTransactionReference();
            });
    }

    /** Inserts (and flushes) the reservation right away, so a parallel duplicate blocks here and fails instead of moving money. */
    public IdempotencyRecord reserve(String key, User user, String endpoint, String requestHash) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setKeyHash(hashKey(key, user));
        record.setEndpoint(endpoint);
        record.setUserId(user.getId());
        record.setRequestHash(requestHash);
        record.setResponseStatus(STATUS_PENDING);
        record.setCreatedAt(Instant.now());
        return idempotencyRecordRepository.saveAndFlush(record);
    }

    public void complete(IdempotencyRecord record, String transactionReference, Object response) {
        record.setTransactionReference(transactionReference);
        record.setResponseStatus(STATUS_COMPLETED);
        try {
            record.setResponseBody(JSON.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            record.setResponseBody(null); // the body is informational only; replays are rebuilt from the reference
        }
        idempotencyRecordRepository.save(record);
    }

    /** SHA-256 over the business fields of a request (never include the PIN or password). */
    public static String requestHash(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            sb.append(part instanceof java.math.BigDecimal bd ? bd.stripTrailingZeros().toPlainString() : String.valueOf(part));
            sb.append('\u001f');
        }
        return sha256(sb.toString());
    }

    static String hashKey(String key, User user) {
        return sha256(user.getId() + ":" + key);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
