package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import org.springframework.security.access.prepost.PreAuthorize;

import com.zekodnix.zaramoney.repository.LedgerEntryRepository;
import com.zekodnix.zaramoney.service.LedgerEntryQueryService;
import com.zekodnix.zaramoney.service.LedgerEntryService;
import com.zekodnix.zaramoney.service.criteria.LedgerEntryCriteria;
import com.zekodnix.zaramoney.service.dto.LedgerEntryDTO;
import java.util.List;
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
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.zekodnix.zaramoney.domain.LedgerEntry}.
 * Read-only: the ledger is append-only (entries are written only by the money flows; DB trigger rejects UPDATE/DELETE).
 * POST/PUT/PATCH/DELETE were removed on purpose (405).
 * <p>
 * Generated CRUD endpoint: restricted to admins. Customers must use the dedicated *Plus endpoints.
 */
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
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

}
