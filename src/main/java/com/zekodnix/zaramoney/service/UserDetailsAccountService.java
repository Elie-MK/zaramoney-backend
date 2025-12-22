package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.UserDetailsAccount;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.service.dto.UserDetailsAccountDTO;
import com.zekodnix.zaramoney.service.mapper.UserDetailsAccountMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.zekodnix.zaramoney.domain.UserDetailsAccount}.
 */
@Service
@Transactional
public class UserDetailsAccountService {

    private static final Logger LOG = LoggerFactory.getLogger(UserDetailsAccountService.class);

    private final UserDetailsAccountRepository userDetailsAccountRepository;

    private final UserDetailsAccountMapper userDetailsAccountMapper;

    public UserDetailsAccountService(
        UserDetailsAccountRepository userDetailsAccountRepository,
        UserDetailsAccountMapper userDetailsAccountMapper
    ) {
        this.userDetailsAccountRepository = userDetailsAccountRepository;
        this.userDetailsAccountMapper = userDetailsAccountMapper;
    }

    /**
     * Save a userDetailsAccount.
     *
     * @param userDetailsAccountDTO the entity to save.
     * @return the persisted entity.
     */
    public UserDetailsAccountDTO save(UserDetailsAccountDTO userDetailsAccountDTO) {
        LOG.debug("Request to save UserDetailsAccount : {}", userDetailsAccountDTO);
        UserDetailsAccount userDetailsAccount = userDetailsAccountMapper.toEntity(userDetailsAccountDTO);
        userDetailsAccount = userDetailsAccountRepository.save(userDetailsAccount);
        return userDetailsAccountMapper.toDto(userDetailsAccount);
    }

    /**
     * Update a userDetailsAccount.
     *
     * @param userDetailsAccountDTO the entity to save.
     * @return the persisted entity.
     */
    public UserDetailsAccountDTO update(UserDetailsAccountDTO userDetailsAccountDTO) {
        LOG.debug("Request to update UserDetailsAccount : {}", userDetailsAccountDTO);
        UserDetailsAccount userDetailsAccount = userDetailsAccountMapper.toEntity(userDetailsAccountDTO);
        userDetailsAccount = userDetailsAccountRepository.save(userDetailsAccount);
        return userDetailsAccountMapper.toDto(userDetailsAccount);
    }

    /**
     * Partially update a userDetailsAccount.
     *
     * @param userDetailsAccountDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<UserDetailsAccountDTO> partialUpdate(UserDetailsAccountDTO userDetailsAccountDTO) {
        LOG.debug("Request to partially update UserDetailsAccount : {}", userDetailsAccountDTO);

        return userDetailsAccountRepository
            .findById(userDetailsAccountDTO.getId())
            .map(existingUserDetailsAccount -> {
                userDetailsAccountMapper.partialUpdate(existingUserDetailsAccount, userDetailsAccountDTO);

                return existingUserDetailsAccount;
            })
            .map(userDetailsAccountRepository::save)
            .map(userDetailsAccountMapper::toDto);
    }

    /**
     * Get all the userDetailsAccounts with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<UserDetailsAccountDTO> findAllWithEagerRelationships(Pageable pageable) {
        return userDetailsAccountRepository.findAllWithEagerRelationships(pageable).map(userDetailsAccountMapper::toDto);
    }

    /**
     * Get one userDetailsAccount by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<UserDetailsAccountDTO> findOne(Long id) {
        LOG.debug("Request to get UserDetailsAccount : {}", id);
        return userDetailsAccountRepository.findOneWithEagerRelationships(id).map(userDetailsAccountMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<UserDetailsAccountDTO> findByUserLoginId(Long userLoginId) {
        LOG.debug("Request to get UserDetailsAccount by userLoginId : {}", userLoginId);
        return userDetailsAccountRepository.findByUserLoginId(userLoginId).map(userDetailsAccountMapper::toDto);
    }

    /**
     * Delete the userDetailsAccount by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete UserDetailsAccount : {}", id);
        userDetailsAccountRepository.deleteById(id);
    }

    public Optional<UserDetailsAccountDTO> findByAccountNumber(String accountNumber) {
        LOG.debug("Request to get UserDetailsAccount by userId : {}", accountNumber);
        return userDetailsAccountRepository.findByAccountNumber(accountNumber).map(userDetailsAccountMapper::toDto);
    }
}
