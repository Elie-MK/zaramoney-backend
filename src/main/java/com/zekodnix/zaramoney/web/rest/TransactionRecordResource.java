package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.service.TransactionRecordQueryService;
import com.zekodnix.zaramoney.service.TransactionRecordService;
import com.zekodnix.zaramoney.service.criteria.TransactionRecordCriteria;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
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
 * REST controller for managing {@link com.zekodnix.zaramoney.domain.TransactionRecord}.
 */
@RestController
@RequestMapping("/api/transaction-records")
public class TransactionRecordResource {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRecordResource.class);

    private static final String ENTITY_NAME = "transactionRecord";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TransactionRecordService transactionRecordService;

    private final TransactionRecordRepository transactionRecordRepository;

    private final TransactionRecordQueryService transactionRecordQueryService;

    public TransactionRecordResource(
        TransactionRecordService transactionRecordService,
        TransactionRecordRepository transactionRecordRepository,
        TransactionRecordQueryService transactionRecordQueryService
    ) {
        this.transactionRecordService = transactionRecordService;
        this.transactionRecordRepository = transactionRecordRepository;
        this.transactionRecordQueryService = transactionRecordQueryService;
    }

    /**
     * {@code POST  /transaction-records} : Create a new transactionRecord.
     *
     * @param transactionRecordDTO the transactionRecordDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new transactionRecordDTO, or with status {@code 400 (Bad Request)} if the transactionRecord has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<TransactionRecordDTO> createTransactionRecord(@Valid @RequestBody TransactionRecordDTO transactionRecordDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save TransactionRecord : {}", transactionRecordDTO);
        if (transactionRecordDTO.getId() != null) {
            throw new BadRequestAlertException("A new transactionRecord cannot already have an ID", ENTITY_NAME, "idexists");
        }
        transactionRecordDTO = transactionRecordService.save(transactionRecordDTO);
        return ResponseEntity.created(new URI("/api/transaction-records/" + transactionRecordDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, transactionRecordDTO.getId().toString()))
            .body(transactionRecordDTO);
    }

    /**
     * {@code PUT  /transaction-records/:id} : Updates an existing transactionRecord.
     *
     * @param id the id of the transactionRecordDTO to save.
     * @param transactionRecordDTO the transactionRecordDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionRecordDTO,
     * or with status {@code 400 (Bad Request)} if the transactionRecordDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the transactionRecordDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionRecordDTO> updateTransactionRecord(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody TransactionRecordDTO transactionRecordDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update TransactionRecord : {}, {}", id, transactionRecordDTO);
        if (transactionRecordDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, transactionRecordDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!transactionRecordRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        transactionRecordDTO = transactionRecordService.update(transactionRecordDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, transactionRecordDTO.getId().toString()))
            .body(transactionRecordDTO);
    }

    /**
     * {@code PATCH  /transaction-records/:id} : Partial updates given fields of an existing transactionRecord, field will ignore if it is null
     *
     * @param id the id of the transactionRecordDTO to save.
     * @param transactionRecordDTO the transactionRecordDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionRecordDTO,
     * or with status {@code 400 (Bad Request)} if the transactionRecordDTO is not valid,
     * or with status {@code 404 (Not Found)} if the transactionRecordDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the transactionRecordDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<TransactionRecordDTO> partialUpdateTransactionRecord(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody TransactionRecordDTO transactionRecordDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update TransactionRecord partially : {}, {}", id, transactionRecordDTO);
        if (transactionRecordDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, transactionRecordDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!transactionRecordRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<TransactionRecordDTO> result = transactionRecordService.partialUpdate(transactionRecordDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, transactionRecordDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /transaction-records} : get all the transactionRecords.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of transactionRecords in body.
     */
    @GetMapping("")
    public ResponseEntity<List<TransactionRecordDTO>> getAllTransactionRecords(
        TransactionRecordCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get TransactionRecords by criteria: {}", criteria);

        Page<TransactionRecordDTO> page = transactionRecordQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /transaction-records/count} : count all the transactionRecords.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countTransactionRecords(TransactionRecordCriteria criteria) {
        LOG.debug("REST request to count TransactionRecords by criteria: {}", criteria);
        return ResponseEntity.ok().body(transactionRecordQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /transaction-records/:id} : get the "id" transactionRecord.
     *
     * @param id the id of the transactionRecordDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the transactionRecordDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionRecordDTO> getTransactionRecord(@PathVariable("id") Long id) {
        LOG.debug("REST request to get TransactionRecord : {}", id);
        Optional<TransactionRecordDTO> transactionRecordDTO = transactionRecordService.findOne(id);
        return ResponseUtil.wrapOrNotFound(transactionRecordDTO);
    }

    /**
     * {@code DELETE  /transaction-records/:id} : delete the "id" transactionRecord.
     *
     * @param id the id of the transactionRecordDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransactionRecord(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete TransactionRecord : {}", id);
        transactionRecordService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
