package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.repository.LedgerEntryRepository;
import com.zekodnix.zaramoney.service.LedgerEntryQueryService;
import com.zekodnix.zaramoney.service.LedgerEntryService;
import com.zekodnix.zaramoney.service.criteria.LedgerEntryCriteria;
import com.zekodnix.zaramoney.service.dto.LedgerEntryDTO;
import com.zekodnix.zaramoney.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.zekodnix.zaramoney.domain.LedgerEntry}.
 */
@RestController
@RequestMapping("/api/ledger-entries")
public class LedgerEntryResource {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerEntryResource.class);

    private static final String ENTITY_NAME = "ledgerEntry";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final LedgerEntryService ledgerEntryService;

    private final LedgerEntryRepository ledgerEntryRepository;

    private final LedgerEntryQueryService ledgerEntryQueryService;

    public LedgerEntryResource(
        LedgerEntryService ledgerEntryService,
        LedgerEntryRepository ledgerEntryRepository,
        LedgerEntryQueryService ledgerEntryQueryService
    ) {
        this.ledgerEntryService = ledgerEntryService;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerEntryQueryService = ledgerEntryQueryService;
    }

    /**
     * {@code POST  /ledger-entries} : Create a new ledgerEntry.
     *
     * @param ledgerEntryDTO the ledgerEntryDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new ledgerEntryDTO, or with status {@code 400 (Bad Request)} if the ledgerEntry has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<LedgerEntryDTO> createLedgerEntry(@Valid @RequestBody LedgerEntryDTO ledgerEntryDTO) throws URISyntaxException {
        LOG.debug("REST request to save LedgerEntry : {}", ledgerEntryDTO);
        if (ledgerEntryDTO.getId() != null) {
            throw new BadRequestAlertException("A new ledgerEntry cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ledgerEntryDTO = ledgerEntryService.save(ledgerEntryDTO);
        return ResponseEntity.created(new URI("/api/ledger-entries/" + ledgerEntryDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, ledgerEntryDTO.getId().toString()))
            .body(ledgerEntryDTO);
    }

    /**
     * {@code PUT  /ledger-entries/:id} : Updates an existing ledgerEntry.
     *
     * @param id the id of the ledgerEntryDTO to save.
     * @param ledgerEntryDTO the ledgerEntryDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated ledgerEntryDTO,
     * or with status {@code 400 (Bad Request)} if the ledgerEntryDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the ledgerEntryDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<LedgerEntryDTO> updateLedgerEntry(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody LedgerEntryDTO ledgerEntryDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update LedgerEntry : {}, {}", id, ledgerEntryDTO);
        if (ledgerEntryDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, ledgerEntryDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!ledgerEntryRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        ledgerEntryDTO = ledgerEntryService.update(ledgerEntryDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, ledgerEntryDTO.getId().toString()))
            .body(ledgerEntryDTO);
    }

    /**
     * {@code PATCH  /ledger-entries/:id} : Partial updates given fields of an existing ledgerEntry, field will ignore if it is null
     *
     * @param id the id of the ledgerEntryDTO to save.
     * @param ledgerEntryDTO the ledgerEntryDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated ledgerEntryDTO,
     * or with status {@code 400 (Bad Request)} if the ledgerEntryDTO is not valid,
     * or with status {@code 404 (Not Found)} if the ledgerEntryDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the ledgerEntryDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<LedgerEntryDTO> partialUpdateLedgerEntry(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody LedgerEntryDTO ledgerEntryDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update LedgerEntry partially : {}, {}", id, ledgerEntryDTO);
        if (ledgerEntryDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, ledgerEntryDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!ledgerEntryRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<LedgerEntryDTO> result = ledgerEntryService.partialUpdate(ledgerEntryDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, ledgerEntryDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /ledger-entries} : get all the ledgerEntries.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of ledgerEntries in body.
     */
    @GetMapping("")
    public ResponseEntity<List<LedgerEntryDTO>> getAllLedgerEntries(
        LedgerEntryCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get LedgerEntries by criteria: {}", criteria);

        Page<LedgerEntryDTO> page = ledgerEntryQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /ledger-entries/count} : count all the ledgerEntries.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countLedgerEntries(LedgerEntryCriteria criteria) {
        LOG.debug("REST request to count LedgerEntries by criteria: {}", criteria);
        return ResponseEntity.ok().body(ledgerEntryQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /ledger-entries/:id} : get the "id" ledgerEntry.
     *
     * @param id the id of the ledgerEntryDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the ledgerEntryDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<LedgerEntryDTO> getLedgerEntry(@PathVariable("id") Long id) {
        LOG.debug("REST request to get LedgerEntry : {}", id);
        Optional<LedgerEntryDTO> ledgerEntryDTO = ledgerEntryService.findOne(id);
        return ResponseUtil.wrapOrNotFound(ledgerEntryDTO);
    }

    /**
     * {@code DELETE  /ledger-entries/:id} : delete the "id" ledgerEntry.
     *
     * @param id the id of the ledgerEntryDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLedgerEntry(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete LedgerEntry : {}", id);
        ledgerEntryService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
