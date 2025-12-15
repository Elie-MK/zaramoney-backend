package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.mapper.TransactionRecordMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.zekodnix.zaramoney.domain.TransactionRecord}.
 */
@Service
@Transactional
public class TransactionRecordService {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRecordService.class);

    private final TransactionRecordRepository transactionRecordRepository;

    private final TransactionRecordMapper transactionRecordMapper;

    public TransactionRecordService(
        TransactionRecordRepository transactionRecordRepository,
        TransactionRecordMapper transactionRecordMapper
    ) {
        this.transactionRecordRepository = transactionRecordRepository;
        this.transactionRecordMapper = transactionRecordMapper;
    }

    /**
     * Save a transactionRecord.
     *
     * @param transactionRecordDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionRecordDTO save(TransactionRecordDTO transactionRecordDTO) {
        LOG.debug("Request to save TransactionRecord : {}", transactionRecordDTO);
        TransactionRecord transactionRecord = transactionRecordMapper.toEntity(transactionRecordDTO);
        transactionRecord = transactionRecordRepository.save(transactionRecord);
        return transactionRecordMapper.toDto(transactionRecord);
    }

    /**
     * Update a transactionRecord.
     *
     * @param transactionRecordDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionRecordDTO update(TransactionRecordDTO transactionRecordDTO) {
        LOG.debug("Request to update TransactionRecord : {}", transactionRecordDTO);
        TransactionRecord transactionRecord = transactionRecordMapper.toEntity(transactionRecordDTO);
        transactionRecord = transactionRecordRepository.save(transactionRecord);
        return transactionRecordMapper.toDto(transactionRecord);
    }

    /**
     * Partially update a transactionRecord.
     *
     * @param transactionRecordDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<TransactionRecordDTO> partialUpdate(TransactionRecordDTO transactionRecordDTO) {
        LOG.debug("Request to partially update TransactionRecord : {}", transactionRecordDTO);

        return transactionRecordRepository
            .findById(transactionRecordDTO.getId())
            .map(existingTransactionRecord -> {
                transactionRecordMapper.partialUpdate(existingTransactionRecord, transactionRecordDTO);

                return existingTransactionRecord;
            })
            .map(transactionRecordRepository::save)
            .map(transactionRecordMapper::toDto);
    }

    /**
     * Get all the transactionRecords with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<TransactionRecordDTO> findAllWithEagerRelationships(Pageable pageable) {
        return transactionRecordRepository.findAllWithEagerRelationships(pageable).map(transactionRecordMapper::toDto);
    }

    /**
     * Get one transactionRecord by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionRecordDTO> findOne(Long id) {
        LOG.debug("Request to get TransactionRecord : {}", id);
        return transactionRecordRepository.findOneWithEagerRelationships(id).map(transactionRecordMapper::toDto);
    }

    /**
     * Delete the transactionRecord by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete TransactionRecord : {}", id);
        transactionRecordRepository.deleteById(id);
    }
}
