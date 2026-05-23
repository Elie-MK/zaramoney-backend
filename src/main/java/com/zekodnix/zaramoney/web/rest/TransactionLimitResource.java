package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.repository.TransactionLimitRepository;
import com.zekodnix.zaramoney.service.TransactionLimitQueryService;
import com.zekodnix.zaramoney.service.TransactionLimitService;
import com.zekodnix.zaramoney.service.criteria.TransactionLimitCriteria;
import com.zekodnix.zaramoney.service.dto.TransactionLimitDTO;
import com.zekodnix.zaramoney.web.rest.errors.BadRequestAlertException;
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
 * REST controller for managing {@link com.zekodnix.zaramoney.domain.TransactionLimit}.
 */
@RestController
@RequestMapping("/api/transaction-limits")
public class TransactionLimitResource {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionLimitResource.class);

    private static final String ENTITY_NAME = "transactionLimit";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TransactionLimitService transactionLimitService;

    private final TransactionLimitRepository transactionLimitRepository;

    private final TransactionLimitQueryService transactionLimitQueryService;

    public TransactionLimitResource(
        TransactionLimitService transactionLimitService,
        TransactionLimitRepository transactionLimitRepository,
        TransactionLimitQueryService transactionLimitQueryService
    ) {
        this.transactionLimitService = transactionLimitService;
        this.transactionLimitRepository = transactionLimitRepository;
        this.transactionLimitQueryService = transactionLimitQueryService;
    }

    /**
     * {@code POST  /transaction-limits} : Create a new transactionLimit.
     *
     * @param transactionLimitDTO the transactionLimitDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new transactionLimitDTO, or with status {@code 400 (Bad Request)} if the transactionLimit has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<TransactionLimitDTO> createTransactionLimit(@RequestBody TransactionLimitDTO transactionLimitDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save TransactionLimit : {}", transactionLimitDTO);
        if (transactionLimitDTO.getId() != null) {
            throw new BadRequestAlertException("A new transactionLimit cannot already have an ID", ENTITY_NAME, "idexists");
        }
        transactionLimitDTO = transactionLimitService.save(transactionLimitDTO);
        return ResponseEntity.created(new URI("/api/transaction-limits/" + transactionLimitDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, transactionLimitDTO.getId().toString()))
            .body(transactionLimitDTO);
    }

    /**
     * {@code PUT  /transaction-limits/:id} : Updates an existing transactionLimit.
     *
     * @param id the id of the transactionLimitDTO to save.
     * @param transactionLimitDTO the transactionLimitDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionLimitDTO,
     * or with status {@code 400 (Bad Request)} if the transactionLimitDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the transactionLimitDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionLimitDTO> updateTransactionLimit(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody TransactionLimitDTO transactionLimitDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update TransactionLimit : {}, {}", id, transactionLimitDTO);
        if (transactionLimitDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, transactionLimitDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!transactionLimitRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        transactionLimitDTO = transactionLimitService.update(transactionLimitDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, transactionLimitDTO.getId().toString()))
            .body(transactionLimitDTO);
    }

    /**
     * {@code PATCH  /transaction-limits/:id} : Partial updates given fields of an existing transactionLimit, field will ignore if it is null
     *
     * @param id the id of the transactionLimitDTO to save.
     * @param transactionLimitDTO the transactionLimitDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionLimitDTO,
     * or with status {@code 400 (Bad Request)} if the transactionLimitDTO is not valid,
     * or with status {@code 404 (Not Found)} if the transactionLimitDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the transactionLimitDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<TransactionLimitDTO> partialUpdateTransactionLimit(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody TransactionLimitDTO transactionLimitDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update TransactionLimit partially : {}, {}", id, transactionLimitDTO);
        if (transactionLimitDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, transactionLimitDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!transactionLimitRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<TransactionLimitDTO> result = transactionLimitService.partialUpdate(transactionLimitDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, transactionLimitDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /transaction-limits} : get all the transactionLimits.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of transactionLimits in body.
     */
    @GetMapping("")
    public ResponseEntity<List<TransactionLimitDTO>> getAllTransactionLimits(
        TransactionLimitCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get TransactionLimits by criteria: {}", criteria);

        Page<TransactionLimitDTO> page = transactionLimitQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /transaction-limits/count} : count all the transactionLimits.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countTransactionLimits(TransactionLimitCriteria criteria) {
        LOG.debug("REST request to count TransactionLimits by criteria: {}", criteria);
        return ResponseEntity.ok().body(transactionLimitQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /transaction-limits/:id} : get the "id" transactionLimit.
     *
     * @param id the id of the transactionLimitDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the transactionLimitDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionLimitDTO> getTransactionLimit(@PathVariable("id") Long id) {
        LOG.debug("REST request to get TransactionLimit : {}", id);
        Optional<TransactionLimitDTO> transactionLimitDTO = transactionLimitService.findOne(id);
        return ResponseUtil.wrapOrNotFound(transactionLimitDTO);
    }

    /**
     * {@code DELETE  /transaction-limits/:id} : delete the "id" transactionLimit.
     *
     * @param id the id of the transactionLimitDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransactionLimit(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete TransactionLimit : {}", id);
        transactionLimitService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
