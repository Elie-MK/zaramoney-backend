package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.IdempotencyRecord;
import com.zekodnix.zaramoney.repository.IdempotencyRecordRepository;
import com.zekodnix.zaramoney.service.dto.IdempotencyRecordDTO;
import com.zekodnix.zaramoney.service.mapper.IdempotencyRecordMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.zekodnix.zaramoney.domain.IdempotencyRecord}.
 */
@Service
@Transactional
public class IdempotencyRecordService {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyRecordService.class);

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    private final IdempotencyRecordMapper idempotencyRecordMapper;

    public IdempotencyRecordService(
        IdempotencyRecordRepository idempotencyRecordRepository,
        IdempotencyRecordMapper idempotencyRecordMapper
    ) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.idempotencyRecordMapper = idempotencyRecordMapper;
    }

    /**
     * Save a idempotencyRecord.
     *
     * @param idempotencyRecordDTO the entity to save.
     * @return the persisted entity.
     */
    public IdempotencyRecordDTO save(IdempotencyRecordDTO idempotencyRecordDTO) {
        LOG.debug("Request to save IdempotencyRecord : {}", idempotencyRecordDTO);
        IdempotencyRecord idempotencyRecord = idempotencyRecordMapper.toEntity(idempotencyRecordDTO);
        idempotencyRecord = idempotencyRecordRepository.save(idempotencyRecord);
        return idempotencyRecordMapper.toDto(idempotencyRecord);
    }

    /**
     * Update a idempotencyRecord.
     *
     * @param idempotencyRecordDTO the entity to save.
     * @return the persisted entity.
     */
    public IdempotencyRecordDTO update(IdempotencyRecordDTO idempotencyRecordDTO) {
        LOG.debug("Request to update IdempotencyRecord : {}", idempotencyRecordDTO);
        IdempotencyRecord idempotencyRecord = idempotencyRecordMapper.toEntity(idempotencyRecordDTO);
        idempotencyRecord = idempotencyRecordRepository.save(idempotencyRecord);
        return idempotencyRecordMapper.toDto(idempotencyRecord);
    }

    /**
     * Partially update a idempotencyRecord.
     *
     * @param idempotencyRecordDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<IdempotencyRecordDTO> partialUpdate(IdempotencyRecordDTO idempotencyRecordDTO) {
        LOG.debug("Request to partially update IdempotencyRecord : {}", idempotencyRecordDTO);

        return idempotencyRecordRepository
            .findById(idempotencyRecordDTO.getId())
            .map(existingIdempotencyRecord -> {
                idempotencyRecordMapper.partialUpdate(existingIdempotencyRecord, idempotencyRecordDTO);

                return existingIdempotencyRecord;
            })
            .map(idempotencyRecordRepository::save)
            .map(idempotencyRecordMapper::toDto);
    }

    /**
     * Get one idempotencyRecord by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecordDTO> findOne(Long id) {
        LOG.debug("Request to get IdempotencyRecord : {}", id);
        return idempotencyRecordRepository.findById(id).map(idempotencyRecordMapper::toDto);
    }

    /**
     * Get one idempotencyRecord by idempotencyKey.
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecordDTO> findByKeyHash(String keyHash) {
        LOG.debug("Request to get IdempotencyRecord by keyHash : {}", keyHash);
        return idempotencyRecordRepository.findByKeyHash(keyHash).map(idempotencyRecordMapper::toDto);
    }

    /**
     * Delete the idempotencyRecord by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete IdempotencyRecord : {}", id);
        idempotencyRecordRepository.deleteById(id);
    }
}
