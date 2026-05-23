package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.*; // for static metamodels
import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.service.criteria.BankAccountCriteria;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.mapper.BankAccountMapper;
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
 * Service for executing complex queries for {@link BankAccount} entities in the database.
 * The main input is a {@link BankAccountCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link BankAccountDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class BankAccountQueryService extends QueryService<BankAccount> {

    private static final Logger LOG = LoggerFactory.getLogger(BankAccountQueryService.class);

    private final BankAccountRepository bankAccountRepository;

    private final BankAccountMapper bankAccountMapper;

    public BankAccountQueryService(BankAccountRepository bankAccountRepository, BankAccountMapper bankAccountMapper) {
        this.bankAccountRepository = bankAccountRepository;
        this.bankAccountMapper = bankAccountMapper;
    }

    /**
     * Return a {@link Page} of {@link BankAccountDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<BankAccountDTO> findByCriteria(BankAccountCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<BankAccount> specification = createSpecification(criteria);
        return bankAccountRepository.findAll(specification, page).map(bankAccountMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(BankAccountCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<BankAccount> specification = createSpecification(criteria);
        return bankAccountRepository.count(specification);
    }

    /**
     * Function to convert {@link BankAccountCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<BankAccount> createSpecification(BankAccountCriteria criteria) {
        Specification<BankAccount> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), BankAccount_.id),
                buildStringSpecification(criteria.getAccountNumber(), BankAccount_.accountNumber),
                buildRangeSpecification(criteria.getBalance(), BankAccount_.balance),
                buildSpecification(criteria.getCurrency(), BankAccount_.currency),
                buildSpecification(criteria.getStatus(), BankAccount_.status),
                buildRangeSpecification(criteria.getCreatedAt(), BankAccount_.createdAt),
                buildSpecification(criteria.getUserId(), root -> root.join(BankAccount_.user, JoinType.LEFT).get(User_.id)),
                buildSpecification(criteria.getTransactionLimitId(), root ->
                    root.join(BankAccount_.transactionLimit, JoinType.LEFT).get(TransactionLimit_.id)
                )
            );
        }
        return specification;
    }
}
