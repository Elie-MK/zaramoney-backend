package com.zekodnix.zaramoney.service.dto;

import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.FraudReviewOutcome;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A SUSPICIOUS / BLOCKED transaction in the admin fraud review queue. {@code transactionStatus} COMPLETED + fraudStatus
 * BLOCKED = "would have been blocked" (monitor mode); FAILED + BLOCKED = rejected (enforce mode, no money moved).
 */
public record FraudCaseDTO(
    Long id,
    String transactionReference,
    TransactionType transactionType,
    TransactionStatus transactionStatus,
    BigDecimal amount,
    BigDecimal fee,
    Currency currency,
    Instant transactionDate,
    String senderAccountNumber,
    String senderLogin,
    String receiverAccountNumber,
    String receiverLogin,
    Integer riskScore,
    FraudStatus fraudStatus,
    String fraudReasons,
    FraudReviewOutcome reviewOutcome,
    String reviewedBy,
    Instant reviewedAt
) {}
