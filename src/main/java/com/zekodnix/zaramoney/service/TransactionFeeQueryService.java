package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.*; // for static metamodels
import com.zekodnix.zaramoney.domain.TransactionFee;
import com.zekodnix.zaramoney.repository.TransactionFeeRepository;
import com.zekodnix.zaramoney.service.criteria.TransactionFeeCriteria;
import com.zekodnix.zaramoney.service.dto.TransactionFeeDTO;
import com.zekodnix.zaramoney.service.mapper.TransactionFeeMapper;
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
 * Service for executing complex queries for {@link TransactionFee} entities in the database.
 * The main input is a {@link TransactionFeeCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link TransactionFeeDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class TransactionFeeQueryService extends QueryService<TransactionFee> {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionFeeQueryService.class);

    private final TransactionFeeRepository transactionFeeRepository;

    private final TransactionFeeMapper transactionFeeMapper;

    public TransactionFeeQueryService(TransactionFeeRepository transactionFeeRepository, TransactionFeeMapper transactionFeeMapper) {
        this.transactionFeeRepository = transactionFeeRepository;
        this.transactionFeeMapper = transactionFeeMapper;
    }

    /**
     * Return a {@link Page} of {@link TransactionFeeDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<TransactionFeeDTO> findByCriteria(TransactionFeeCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<TransactionFee> specification = createSpecification(criteria);
        return transactionFeeRepository.findAll(specification, page).map(transactionFeeMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(TransactionFeeCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<TransactionFee> specification = createSpecification(criteria);
        return transactionFeeRepository.count(specification);
    }

    /**
     * Function to convert {@link TransactionFeeCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<TransactionFee> createSpecification(TransactionFeeCriteria criteria) {
        Specification<TransactionFee> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), TransactionFee_.id),
                buildRangeSpecification(criteria.getAmount(), TransactionFee_.amount),
                buildSpecification(criteria.getCurrency(), TransactionFee_.currency),
                buildStringSpecification(criteria.getType(), TransactionFee_.type),
                buildSpecification(criteria.getTransactionId(), root ->
                    root.join(TransactionFee_.transaction, JoinType.LEFT).get(TransactionRecord_.id)
                )
            );
        }
        return specification;
    }
}
