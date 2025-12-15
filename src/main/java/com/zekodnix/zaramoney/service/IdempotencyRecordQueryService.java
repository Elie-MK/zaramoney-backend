package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.*; // for static metamodels
import com.zekodnix.zaramoney.domain.IdempotencyRecord;
import com.zekodnix.zaramoney.repository.IdempotencyRecordRepository;
import com.zekodnix.zaramoney.service.criteria.IdempotencyRecordCriteria;
import com.zekodnix.zaramoney.service.dto.IdempotencyRecordDTO;
import com.zekodnix.zaramoney.service.mapper.IdempotencyRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link IdempotencyRecord} entities in the database.
 * The main input is a {@link IdempotencyRecordCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link IdempotencyRecordDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class IdempotencyRecordQueryService extends QueryService<IdempotencyRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyRecordQueryService.class);

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    private final IdempotencyRecordMapper idempotencyRecordMapper;

    public IdempotencyRecordQueryService(
        IdempotencyRecordRepository idempotencyRecordRepository,
        IdempotencyRecordMapper idempotencyRecordMapper
    ) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.idempotencyRecordMapper = idempotencyRecordMapper;
    }

    /**
     * Return a {@link Page} of {@link IdempotencyRecordDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<IdempotencyRecordDTO> findByCriteria(IdempotencyRecordCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<IdempotencyRecord> specification = createSpecification(criteria);
        return idempotencyRecordRepository.findAll(specification, page).map(idempotencyRecordMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(IdempotencyRecordCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<IdempotencyRecord> specification = createSpecification(criteria);
        return idempotencyRecordRepository.count(specification);
    }

    /**
     * Function to convert {@link IdempotencyRecordCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<IdempotencyRecord> createSpecification(IdempotencyRecordCriteria criteria) {
        Specification<IdempotencyRecord> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), IdempotencyRecord_.id),
                buildStringSpecification(criteria.getKeyHash(), IdempotencyRecord_.keyHash),
                buildStringSpecification(criteria.getEndpoint(), IdempotencyRecord_.endpoint),
                buildRangeSpecification(criteria.getUserId(), IdempotencyRecord_.userId),
                buildRangeSpecification(criteria.getCreatedAt(), IdempotencyRecord_.createdAt),
                buildRangeSpecification(criteria.getResponseStatus(), IdempotencyRecord_.responseStatus),
                buildStringSpecification(criteria.getTransactionReference(), IdempotencyRecord_.transactionReference)
            );
        }
        return specification;
    }
}
