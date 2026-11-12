package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.TransactionFee;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.repository.TransactionFeeRepository;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.security.SecurityUtils;
import com.zekodnix.zaramoney.service.dto.TransactionReceiptDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.dto.TransactionReceiptDTO.Direction;
import com.zekodnix.zaramoney.service.utils.MaskingUtils;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds customer-facing {@link TransactionReceiptDTO}s, scoped to the current user: a user can only see
 * transactions where one of their accounts is the sender or the receiver.
 */
@Service
@Transactional(readOnly = true)
public class TransactionReceiptService {

    static final String ZARAMONEY_NAME = "ZaraMoney";

    private final TransactionRecordRepository transactionRecordRepository;
    private final TransactionFeeRepository transactionFeeRepository;

    public TransactionReceiptService(
        TransactionRecordRepository transactionRecordRepository,
        TransactionFeeRepository transactionFeeRepository
    ) {
        this.transactionRecordRepository = transactionRecordRepository;
        this.transactionFeeRepository = transactionFeeRepository;
    }

    public Page<TransactionReceiptDTO> findAllForCurrentUser(Pageable pageable) {
        String login = currentLogin();
        return transactionRecordRepository.findAllForUserLogin(login, pageable).map(tx -> toReceipt(tx, login));
    }

    /** 404 (not 403) when the transaction does not exist or is not the user's, so ids cannot be probed. */
    public TransactionReceiptDTO findOneForCurrentUser(Long id) {
        String login = currentLogin();
        return transactionRecordRepository
            .findById(id)
            .filter(tx -> isParty(tx, login))
            .map(tx -> toReceipt(tx, login))
            .orElseThrow(() -> ApiException.notFound("Transaction not found", "transactionnotfound"));
    }

    public TransactionReceiptDTO findByReferenceForCurrentUser(String transactionReference) {
        String login = currentLogin();
        return transactionRecordRepository
            .findOneByTransactionReference(transactionReference)
            .filter(tx -> isParty(tx, login))
            .map(tx -> toReceipt(tx, login))
            .orElseThrow(() -> ApiException.notFound("Transaction not found", "transactionnotfound"));
    }

    /** Receipt for the sender right after a debit, built from the locked (managed) accounts. */
    public TransactionReceiptDTO buildSentReceipt(TransactionRecordDTO tx, BankAccount own, BankAccount counterparty, BigDecimal fee) {
        TransactionReceiptDTO r = new TransactionReceiptDTO();
        r.setId(tx.getId());
        r.setTransactionReference(tx.getTransactionReference());
        r.setTransactionType(tx.getTransactionType());
        r.setDirection(Direction.SENT);
        r.setStatus(tx.getTransactionStatus());
        r.setDescription(tx.getDescription());
        r.setTransactionDate(tx.getTransactionDate());
        r.setAccountNumber(own.getAccountNumber());
        r.setAmount(tx.getSendAmount());
        r.setFee(fee);
        r.setTotal(tx.getSendAmount().add(fee));
        r.setCurrency(tx.getCurrencySendAmount());
        setCounterparty(r, counterparty);
        return r;
    }

    TransactionReceiptDTO toReceipt(TransactionRecord tx, String viewerLogin) {
        boolean sent = isOwnedBy(tx.getSender(), viewerLogin);
        BankAccount own = sent ? tx.getSender() : tx.getReceiver();
        BankAccount counterparty = sent ? tx.getReceiver() : tx.getSender();

        TransactionReceiptDTO r = new TransactionReceiptDTO();
        r.setId(tx.getId());
        r.setTransactionReference(tx.getTransactionReference());
        r.setTransactionType(tx.getTransactionType());
        r.setDirection(sent ? Direction.SENT : Direction.RECEIVED);
        r.setStatus(tx.getTransactionStatus());
        r.setDescription(tx.getDescription());
        r.setTransactionDate(tx.getTransactionDate() != null ? tx.getTransactionDate() : tx.getCreatedAt());
        r.setAccountNumber(own != null ? own.getAccountNumber() : null);

        if (sent) {
            // fee column since Phase 3 (backfilled); transaction_fee is the fallback for rows the backfill left null
            BigDecimal fee = tx.getFee() != null
                ? tx.getFee()
                : transactionFeeRepository.findFirstByTransactionId(tx.getId()).map(TransactionFee::getAmount).orElse(BigDecimal.ZERO);
            r.setAmount(tx.getSendAmount());
            r.setFee(fee);
            r.setTotal(tx.getSendAmount().add(fee));
            r.setCurrency(tx.getCurrencySendAmount());
        } else {
            r.setAmount(tx.getReceiveAmount());
            r.setTotal(tx.getReceiveAmount());
            r.setCurrency(tx.getCurrencyReceiveAmount());
        }

        setCounterparty(r, counterparty);
        return r;
    }

    private static void setCounterparty(TransactionReceiptDTO r, BankAccount counterparty) {
        if (counterparty == null || counterparty.isSystemAccount() || counterparty.getUser() == null) {
            r.setCounterpartyName(ZARAMONEY_NAME);
            r.setCounterpartyAccountNumber(null);
        } else {
            r.setCounterpartyName(displayName(counterparty.getUser()));
            r.setCounterpartyAccountNumber(MaskingUtils.maskAccountNumber(counterparty.getAccountNumber()));
        }
    }

    /** Customers only see COMPLETED transactions (never a payment blocked by the fraud rules, on either side). */
    private static boolean isParty(TransactionRecord tx, String login) {
        return (
            tx.getTransactionStatus() == TransactionStatus.COMPLETED && (isOwnedBy(tx.getSender(), login) || isOwnedBy(tx.getReceiver(), login))
        );
    }

    private static boolean isOwnedBy(BankAccount account, String login) {
        return account != null && account.getUser() != null && login.equals(account.getUser().getLogin());
    }

    private static String displayName(User user) {
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String name = (first + " " + last).trim();
        return name.isEmpty() ? "Client ZaraMoney" : name;
    }

    private static String currentLogin() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() ->
            new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated", "unauthorized")
        );
    }
}
