package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.TransactionFee;
import com.zekodnix.zaramoney.repository.TransactionFeeRepository;
import com.zekodnix.zaramoney.service.dto.TransactionFeeDTO;
import com.zekodnix.zaramoney.service.mapper.TransactionFeeMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.zekodnix.zaramoney.domain.TransactionFee}.
 */
@Service
@Transactional
public class TransactionFeeService {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionFeeService.class);

    private final TransactionFeeRepository transactionFeeRepository;

    private final TransactionFeeMapper transactionFeeMapper;

    public TransactionFeeService(TransactionFeeRepository transactionFeeRepository, TransactionFeeMapper transactionFeeMapper) {
        this.transactionFeeRepository = transactionFeeRepository;
        this.transactionFeeMapper = transactionFeeMapper;
    }

    /**
     * Save a transactionFee.
     *
     * @param transactionFeeDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionFeeDTO save(TransactionFeeDTO transactionFeeDTO) {
        LOG.debug("Request to save TransactionFee : {}", transactionFeeDTO);
        TransactionFee transactionFee = transactionFeeMapper.toEntity(transactionFeeDTO);
        transactionFee = transactionFeeRepository.save(transactionFee);
        return transactionFeeMapper.toDto(transactionFee);
    }

    /**
     * Update a transactionFee.
     *
     * @param transactionFeeDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionFeeDTO update(TransactionFeeDTO transactionFeeDTO) {
        LOG.debug("Request to update TransactionFee : {}", transactionFeeDTO);
        TransactionFee transactionFee = transactionFeeMapper.toEntity(transactionFeeDTO);
        transactionFee = transactionFeeRepository.save(transactionFee);
        return transactionFeeMapper.toDto(transactionFee);
    }

    /**
     * Partially update a transactionFee.
     *
     * @param transactionFeeDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<TransactionFeeDTO> partialUpdate(TransactionFeeDTO transactionFeeDTO) {
        LOG.debug("Request to partially update TransactionFee : {}", transactionFeeDTO);

        return transactionFeeRepository
            .findById(transactionFeeDTO.getId())
            .map(existingTransactionFee -> {
                transactionFeeMapper.partialUpdate(existingTransactionFee, transactionFeeDTO);

                return existingTransactionFee;
            })
            .map(transactionFeeRepository::save)
            .map(transactionFeeMapper::toDto);
    }

    /**
     * Get all the transactionFees with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<TransactionFeeDTO> findAllWithEagerRelationships(Pageable pageable) {
        return transactionFeeRepository.findAllWithEagerRelationships(pageable).map(transactionFeeMapper::toDto);
    }

    /**
     * Get one transactionFee by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionFeeDTO> findOne(Long id) {
        LOG.debug("Request to get TransactionFee : {}", id);
        return transactionFeeRepository.findOneWithEagerRelationships(id).map(transactionFeeMapper::toDto);
    }

    /**
     * Delete the transactionFee by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete TransactionFee : {}", id);
        transactionFeeRepository.deleteById(id);
    }
}
