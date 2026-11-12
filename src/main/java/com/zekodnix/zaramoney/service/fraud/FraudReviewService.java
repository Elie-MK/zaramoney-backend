package com.zekodnix.zaramoney.service.fraud;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.domain.enumeration.FraudReviewOutcome;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.service.dto.FraudCaseDTO;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin fraud review queue. Reviewing records a decision only: it never moves money (a confirmed fraud is followed up
 * by suspending the account through the admin CRUD and, if needed, a correcting transaction).
 */
@Service
@Transactional
public class FraudReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(FraudReviewService.class);

    private final TransactionRecordRepository transactionRecordRepository;

    public FraudReviewService(TransactionRecordRepository transactionRecordRepository) {
        this.transactionRecordRepository = transactionRecordRepository;
    }

    /** Flagged transactions, newest first. {@code statuses} empty = SUSPICIOUS and BLOCKED. */
    @Transactional(readOnly = true)
    public Page<FraudCaseDTO> queue(Collection<FraudStatus> statuses, boolean unreviewedOnly, Pageable pageable) {
        Collection<FraudStatus> wanted = statuses == null || statuses.isEmpty()
            ? EnumSet.of(FraudStatus.SUSPICIOUS, FraudStatus.BLOCKED)
            : EnumSet.copyOf(statuses);
        wanted.remove(FraudStatus.CLEAN);
        if (wanted.isEmpty()) {
            throw ApiException.badRequest("Only SUSPICIOUS and BLOCKED transactions are reviewed", "invalidfraudstatus");
        }
        return transactionRecordRepository.findFraudQueue(wanted, unreviewedOnly, pageable).map(FraudReviewService::toDto);
    }

    public FraudCaseDTO review(Long transactionId, FraudReviewOutcome outcome, String adminLogin) {
        TransactionRecord tx = transactionRecordRepository
            .findById(transactionId)
            .orElseThrow(() -> ApiException.notFound("Transaction not found", "transactionnotfound"));
        if (tx.getFraudStatus() == null || tx.getFraudStatus() == FraudStatus.CLEAN) {
            throw ApiException.badRequest("This transaction was not flagged", "notflagged");
        }
        FraudReviewOutcome previous = tx.getFraudReviewOutcome();
        tx.setFraudReviewOutcome(outcome);
        tx.setFraudReviewedBy(adminLogin);
        tx.setFraudReviewedAt(Instant.now());
        transactionRecordRepository.save(tx);
        // TODO(Phase 5 audit log): persist instead of the application log
        LOG.info(
            "AUDIT fraud review ref={} {} -> {} (status {}, score {}) by admin {}",
            tx.getTransactionReference(),
            previous,
            outcome,
            tx.getFraudStatus(),
            tx.getRiskScore(),
            adminLogin
        );
        return toDto(tx);
    }

    static FraudCaseDTO toDto(TransactionRecord t) {
        return new FraudCaseDTO(
            t.getId(),
            t.getTransactionReference(),
            t.getTransactionType(),
            t.getTransactionStatus(),
            t.getSendAmount(),
            t.getFee(),
            t.getCurrencySendAmount(),
            t.getTransactionDate(),
            number(t.getSender()),
            login(t.getSender()),
            number(t.getReceiver()),
            login(t.getReceiver()),
            t.getRiskScore(),
            t.getFraudStatus(),
            t.getFraudReasons(),
            t.getFraudReviewOutcome(),
            t.getFraudReviewedBy(),
            t.getFraudReviewedAt()
        );
    }

    private static String number(BankAccount a) {
        return a == null ? null : a.getAccountNumber();
    }

    private static String login(BankAccount a) {
        return a == null || a.getUser() == null ? null : a.getUser().getLogin();
    }
}
