package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.TransactionLimit;
import com.zekodnix.zaramoney.repository.TransactionLimitRepository;
import com.zekodnix.zaramoney.service.dto.TransactionLimitDTO;
import com.zekodnix.zaramoney.service.mapper.TransactionLimitMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.zekodnix.zaramoney.domain.TransactionLimit}.
 */
@Service
@Transactional
public class TransactionLimitService {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionLimitService.class);

    private final TransactionLimitRepository transactionLimitRepository;

    private final TransactionLimitMapper transactionLimitMapper;

    public TransactionLimitService(TransactionLimitRepository transactionLimitRepository, TransactionLimitMapper transactionLimitMapper) {
        this.transactionLimitRepository = transactionLimitRepository;
        this.transactionLimitMapper = transactionLimitMapper;
    }

    /**
     * Save a transactionLimit.
     *
     * @param transactionLimitDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionLimitDTO save(TransactionLimitDTO transactionLimitDTO) {
        LOG.debug("Request to save TransactionLimit : {}", transactionLimitDTO);
        TransactionLimit transactionLimit = transactionLimitMapper.toEntity(transactionLimitDTO);
        transactionLimit = transactionLimitRepository.save(transactionLimit);
        return transactionLimitMapper.toDto(transactionLimit);
    }

    /**
     * Update a transactionLimit.
     *
     * @param transactionLimitDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionLimitDTO update(TransactionLimitDTO transactionLimitDTO) {
        LOG.debug("Request to update TransactionLimit : {}", transactionLimitDTO);
        TransactionLimit transactionLimit = transactionLimitMapper.toEntity(transactionLimitDTO);
        transactionLimit = transactionLimitRepository.save(transactionLimit);
        return transactionLimitMapper.toDto(transactionLimit);
    }

    /**
     * Partially update a transactionLimit.
     *
     * @param transactionLimitDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<TransactionLimitDTO> partialUpdate(TransactionLimitDTO transactionLimitDTO) {
        LOG.debug("Request to partially update TransactionLimit : {}", transactionLimitDTO);

        return transactionLimitRepository
            .findById(transactionLimitDTO.getId())
            .map(existingTransactionLimit -> {
                transactionLimitMapper.partialUpdate(existingTransactionLimit, transactionLimitDTO);

                return existingTransactionLimit;
            })
            .map(transactionLimitRepository::save)
            .map(transactionLimitMapper::toDto);
    }

    /**
     * Get all the transactionLimits with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<TransactionLimitDTO> findAllWithEagerRelationships(Pageable pageable) {
        return transactionLimitRepository.findAllWithEagerRelationships(pageable).map(transactionLimitMapper::toDto);
    }

    /**
     * Get one transactionLimit by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionLimitDTO> findOne(Long id) {
        LOG.debug("Request to get TransactionLimit : {}", id);
        return transactionLimitRepository.findOneWithEagerRelationships(id).map(transactionLimitMapper::toDto);
    }

    /**
     * Delete the transactionLimit by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete TransactionLimit : {}", id);
        transactionLimitRepository.deleteById(id);
    }
}
