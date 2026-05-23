package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.LedgerEntry;
import com.zekodnix.zaramoney.repository.LedgerEntryRepository;
import com.zekodnix.zaramoney.service.dto.LedgerEntryDTO;
import com.zekodnix.zaramoney.service.mapper.LedgerEntryMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.zekodnix.zaramoney.domain.LedgerEntry}.
 */
@Service
@Transactional
public class LedgerEntryService {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerEntryService.class);

    private final LedgerEntryRepository ledgerEntryRepository;

    private final LedgerEntryMapper ledgerEntryMapper;

    public LedgerEntryService(LedgerEntryRepository ledgerEntryRepository, LedgerEntryMapper ledgerEntryMapper) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerEntryMapper = ledgerEntryMapper;
    }

    /**
     * Save a ledgerEntry.
     *
     * @param ledgerEntryDTO the entity to save.
     * @return the persisted entity.
     */
    public LedgerEntryDTO save(LedgerEntryDTO ledgerEntryDTO) {
        LOG.debug("Request to save LedgerEntry : {}", ledgerEntryDTO);
        LedgerEntry ledgerEntry = ledgerEntryMapper.toEntity(ledgerEntryDTO);
        ledgerEntry = ledgerEntryRepository.save(ledgerEntry);
        return ledgerEntryMapper.toDto(ledgerEntry);
    }

    /**
     * Update a ledgerEntry.
     *
     * @param ledgerEntryDTO the entity to save.
     * @return the persisted entity.
     */
    public LedgerEntryDTO update(LedgerEntryDTO ledgerEntryDTO) {
        LOG.debug("Request to update LedgerEntry : {}", ledgerEntryDTO);
        LedgerEntry ledgerEntry = ledgerEntryMapper.toEntity(ledgerEntryDTO);
        ledgerEntry = ledgerEntryRepository.save(ledgerEntry);
        return ledgerEntryMapper.toDto(ledgerEntry);
    }

    /**
     * Partially update a ledgerEntry.
     *
     * @param ledgerEntryDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<LedgerEntryDTO> partialUpdate(LedgerEntryDTO ledgerEntryDTO) {
        LOG.debug("Request to partially update LedgerEntry : {}", ledgerEntryDTO);

        return ledgerEntryRepository
            .findById(ledgerEntryDTO.getId())
            .map(existingLedgerEntry -> {
                ledgerEntryMapper.partialUpdate(existingLedgerEntry, ledgerEntryDTO);

                return existingLedgerEntry;
            })
            .map(ledgerEntryRepository::save)
            .map(ledgerEntryMapper::toDto);
    }

    /**
     * Get all the ledgerEntries with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<LedgerEntryDTO> findAllWithEagerRelationships(Pageable pageable) {
        return ledgerEntryRepository.findAllWithEagerRelationships(pageable).map(ledgerEntryMapper::toDto);
    }

    /**
     * Get one ledgerEntry by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<LedgerEntryDTO> findOne(Long id) {
        LOG.debug("Request to get LedgerEntry : {}", id);
        return ledgerEntryRepository.findOneWithEagerRelationships(id).map(ledgerEntryMapper::toDto);
    }

    /**
     * Delete the ledgerEntry by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete LedgerEntry : {}", id);
        ledgerEntryRepository.deleteById(id);
    }
}
