package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.*; // for static metamodels
import com.zekodnix.zaramoney.domain.LedgerEntry;
import com.zekodnix.zaramoney.repository.LedgerEntryRepository;
import com.zekodnix.zaramoney.service.criteria.LedgerEntryCriteria;
import com.zekodnix.zaramoney.service.dto.LedgerEntryDTO;
import com.zekodnix.zaramoney.service.mapper.LedgerEntryMapper;
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
 * Service for executing complex queries for {@link LedgerEntry} entities in the database.
 * The main input is a {@link LedgerEntryCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link LedgerEntryDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class LedgerEntryQueryService extends QueryService<LedgerEntry> {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerEntryQueryService.class);

    private final LedgerEntryRepository ledgerEntryRepository;

    private final LedgerEntryMapper ledgerEntryMapper;

    public LedgerEntryQueryService(LedgerEntryRepository ledgerEntryRepository, LedgerEntryMapper ledgerEntryMapper) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerEntryMapper = ledgerEntryMapper;
    }

    /**
     * Return a {@link Page} of {@link LedgerEntryDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntryDTO> findByCriteria(LedgerEntryCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<LedgerEntry> specification = createSpecification(criteria);
        return ledgerEntryRepository.findAll(specification, page).map(ledgerEntryMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(LedgerEntryCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<LedgerEntry> specification = createSpecification(criteria);
        return ledgerEntryRepository.count(specification);
    }

    /**
     * Function to convert {@link LedgerEntryCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<LedgerEntry> createSpecification(LedgerEntryCriteria criteria) {
        Specification<LedgerEntry> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), LedgerEntry_.id),
                buildRangeSpecification(criteria.getAmount(), LedgerEntry_.amount),
                buildSpecification(criteria.getCurrency(), LedgerEntry_.currency),
                buildSpecification(criteria.getEntryType(), LedgerEntry_.entryType),
                buildRangeSpecification(criteria.getCreatedAt(), LedgerEntry_.createdAt),
                buildSpecification(criteria.getAccountId(), root -> root.join(LedgerEntry_.account, JoinType.LEFT).get(BankAccount_.id)),
                buildSpecification(criteria.getTransactionId(), root ->
                    root.join(LedgerEntry_.transaction, JoinType.LEFT).get(TransactionRecord_.id)
                )
            );
        }
        return specification;
    }
}
