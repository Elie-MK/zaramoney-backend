package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.*; // for static metamodels
import com.zekodnix.zaramoney.domain.UserDetailsAccount;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.service.criteria.UserDetailsAccountCriteria;
import com.zekodnix.zaramoney.service.dto.UserDetailsAccountDTO;
import com.zekodnix.zaramoney.service.mapper.UserDetailsAccountMapper;
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
 * Service for executing complex queries for {@link UserDetailsAccount} entities in the database.
 * The main input is a {@link UserDetailsAccountCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link UserDetailsAccountDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class UserDetailsAccountQueryService extends QueryService<UserDetailsAccount> {

    private static final Logger LOG = LoggerFactory.getLogger(UserDetailsAccountQueryService.class);

    private final UserDetailsAccountRepository userDetailsAccountRepository;

    private final UserDetailsAccountMapper userDetailsAccountMapper;

    public UserDetailsAccountQueryService(
        UserDetailsAccountRepository userDetailsAccountRepository,
        UserDetailsAccountMapper userDetailsAccountMapper
    ) {
        this.userDetailsAccountRepository = userDetailsAccountRepository;
        this.userDetailsAccountMapper = userDetailsAccountMapper;
    }

    /**
     * Return a {@link Page} of {@link UserDetailsAccountDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<UserDetailsAccountDTO> findByCriteria(UserDetailsAccountCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<UserDetailsAccount> specification = createSpecification(criteria);
        return userDetailsAccountRepository.findAll(specification, page).map(userDetailsAccountMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(UserDetailsAccountCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<UserDetailsAccount> specification = createSpecification(criteria);
        return userDetailsAccountRepository.count(specification);
    }

    /**
     * Function to convert {@link UserDetailsAccountCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<UserDetailsAccount> createSpecification(UserDetailsAccountCriteria criteria) {
        Specification<UserDetailsAccount> specification = Specification.where(null);
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = Specification.allOf(
                Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : null,
                buildRangeSpecification(criteria.getId(), UserDetailsAccount_.id),
                buildStringSpecification(criteria.getPhoneNumber(), UserDetailsAccount_.phoneNumber),
                buildStringSpecification(criteria.getFacePicture(), UserDetailsAccount_.facePicture),
                buildStringSpecification(criteria.getIdCardPicture(), UserDetailsAccount_.idCardPicture),
                buildStringSpecification(criteria.getCountry(), UserDetailsAccount_.country),
                buildStringSpecification(criteria.getAddress(), UserDetailsAccount_.address),
                buildSpecification(criteria.getIsAgent(), UserDetailsAccount_.isAgent),
                buildStringSpecification(criteria.getAccountNumber(), UserDetailsAccount_.accountNumber),
                buildRangeSpecification(criteria.getAccountBalance(), UserDetailsAccount_.accountBalance),
                buildSpecification(criteria.getUserLoginId(), root -> root.join(UserDetailsAccount_.userLogin, JoinType.LEFT).get(User_.id))
            );
        }
        return specification;
    }
}
