package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.*; // for static metamodels
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.service.criteria.TransactionRecordCriteria;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.mapper.TransactionRecordMapper;
import jakarta.persistence.criteria.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link TransactionRecord} entities in the database.
 * The main input is a {@link TransactionRecordCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link TransactionRecordDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class TransactionRecordQueryService extends QueryService<TransactionRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRecordQueryService.class);

    private final TransactionRecordRepository transactionRecordRepository;

    private final TransactionRecordMapper transactionRecordMapper;

    public TransactionRecordQueryService(
        TransactionRecordRepository transactionRecordRepository,
        TransactionRecordMapper transactionRecordMapper
    ) {
        this.transactionRecordRepository = transactionRecordRepository;
        this.transactionRecordMapper = transactionRecordMapper;
    }

    /**
     * Return a {@link Page} of {@link TransactionRecordDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<TransactionRecordDTO> findByCriteria(TransactionRecordCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<TransactionRecord> specification = createSpecification(criteria);
        return transactionRecordRepository.findAll(specification, page).map(transactionRecordMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(TransactionRecordCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<TransactionRecord> specification = createSpecification(criteria);
        return transactionRecordRepository.count(specification);
    }

    /**
     * Function to convert {@link TransactionRecordCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<TransactionRecord> createSpecification(TransactionRecordCriteria criteria) {
        Specification<TransactionRecord> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), TransactionRecord_.id),
                buildSpecification(criteria.getTransactionType(), TransactionRecord_.transactionType),
                buildRangeSpecification(criteria.getSendAmount(), TransactionRecord_.sendAmount),
                buildRangeSpecification(criteria.getReceiveAmount(), TransactionRecord_.receiveAmount),
                buildRangeSpecification(criteria.getTransactionDate(), TransactionRecord_.transactionDate),
                buildStringSpecification(criteria.getDescription(), TransactionRecord_.description),
                buildStringSpecification(criteria.getSenderAccountNumber(), TransactionRecord_.senderAccountNumber),
                buildStringSpecification(criteria.getReceiverAccountNumber(), TransactionRecord_.receiverAccountNumber),
                buildSpecification(criteria.getCurrencySendAmount(), TransactionRecord_.currencySendAmount),
                buildSpecification(criteria.getCurrencyReceiveAmount(), TransactionRecord_.currencyReceiveAmount),
                buildSpecification(criteria.getTransactionStatus(), TransactionRecord_.transactionStatus),
                buildStringSpecification(criteria.getTransactionReference(), TransactionRecord_.transactionReference),
                buildRangeSpecification(criteria.getRiskScore(), TransactionRecord_.riskScore),
                buildSpecification(criteria.getFraudStatus(), TransactionRecord_.fraudStatus),
                buildRangeSpecification(criteria.getCreatedAt(), TransactionRecord_.createdAt),
                buildRangeSpecification(criteria.getUpdatedAt(), TransactionRecord_.updatedAt),
                buildSpecification(criteria.getUserLoginId(), root -> root.join(TransactionRecord_.userLogin, JoinType.LEFT).get(User_.id))
            );
        }
        return specification;
    }
}
