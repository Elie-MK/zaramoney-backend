package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.repository.TransactionFeeRepository;
import com.zekodnix.zaramoney.service.TransactionFeeQueryService;
import com.zekodnix.zaramoney.service.TransactionFeeService;
import com.zekodnix.zaramoney.service.criteria.TransactionFeeCriteria;
import com.zekodnix.zaramoney.service.dto.TransactionFeeDTO;
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
 * REST controller for managing {@link com.zekodnix.zaramoney.domain.TransactionFee}.
 */
@RestController
@RequestMapping("/api/transaction-fees")
public class TransactionFeeResource {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionFeeResource.class);

    private static final String ENTITY_NAME = "transactionFee";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TransactionFeeService transactionFeeService;

    private final TransactionFeeRepository transactionFeeRepository;

    private final TransactionFeeQueryService transactionFeeQueryService;

    public TransactionFeeResource(
        TransactionFeeService transactionFeeService,
        TransactionFeeRepository transactionFeeRepository,
        TransactionFeeQueryService transactionFeeQueryService
    ) {
        this.transactionFeeService = transactionFeeService;
        this.transactionFeeRepository = transactionFeeRepository;
        this.transactionFeeQueryService = transactionFeeQueryService;
    }

    /**
     * {@code POST  /transaction-fees} : Create a new transactionFee.
     *
     * @param transactionFeeDTO the transactionFeeDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new transactionFeeDTO, or with status {@code 400 (Bad Request)} if the transactionFee has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<TransactionFeeDTO> createTransactionFee(@Valid @RequestBody TransactionFeeDTO transactionFeeDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save TransactionFee : {}", transactionFeeDTO);
        if (transactionFeeDTO.getId() != null) {
            throw new BadRequestAlertException("A new transactionFee cannot already have an ID", ENTITY_NAME, "idexists");
        }
        transactionFeeDTO = transactionFeeService.save(transactionFeeDTO);
        return ResponseEntity.created(new URI("/api/transaction-fees/" + transactionFeeDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, transactionFeeDTO.getId().toString()))
            .body(transactionFeeDTO);
    }

    /**
     * {@code PUT  /transaction-fees/:id} : Updates an existing transactionFee.
     *
     * @param id the id of the transactionFeeDTO to save.
     * @param transactionFeeDTO the transactionFeeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionFeeDTO,
     * or with status {@code 400 (Bad Request)} if the transactionFeeDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the transactionFeeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionFeeDTO> updateTransactionFee(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody TransactionFeeDTO transactionFeeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update TransactionFee : {}, {}", id, transactionFeeDTO);
        if (transactionFeeDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, transactionFeeDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!transactionFeeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        transactionFeeDTO = transactionFeeService.update(transactionFeeDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, transactionFeeDTO.getId().toString()))
            .body(transactionFeeDTO);
    }

    /**
     * {@code PATCH  /transaction-fees/:id} : Partial updates given fields of an existing transactionFee, field will ignore if it is null
     *
     * @param id the id of the transactionFeeDTO to save.
     * @param transactionFeeDTO the transactionFeeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated transactionFeeDTO,
     * or with status {@code 400 (Bad Request)} if the transactionFeeDTO is not valid,
     * or with status {@code 404 (Not Found)} if the transactionFeeDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the transactionFeeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<TransactionFeeDTO> partialUpdateTransactionFee(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody TransactionFeeDTO transactionFeeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update TransactionFee partially : {}, {}", id, transactionFeeDTO);
        if (transactionFeeDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, transactionFeeDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!transactionFeeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<TransactionFeeDTO> result = transactionFeeService.partialUpdate(transactionFeeDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, transactionFeeDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /transaction-fees} : get all the transactionFees.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of transactionFees in body.
     */
    @GetMapping("")
    public ResponseEntity<List<TransactionFeeDTO>> getAllTransactionFees(
        TransactionFeeCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get TransactionFees by criteria: {}", criteria);

        Page<TransactionFeeDTO> page = transactionFeeQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /transaction-fees/count} : count all the transactionFees.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countTransactionFees(TransactionFeeCriteria criteria) {
        LOG.debug("REST request to count TransactionFees by criteria: {}", criteria);
        return ResponseEntity.ok().body(transactionFeeQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /transaction-fees/:id} : get the "id" transactionFee.
     *
     * @param id the id of the transactionFeeDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the transactionFeeDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionFeeDTO> getTransactionFee(@PathVariable("id") Long id) {
        LOG.debug("REST request to get TransactionFee : {}", id);
        Optional<TransactionFeeDTO> transactionFeeDTO = transactionFeeService.findOne(id);
        return ResponseUtil.wrapOrNotFound(transactionFeeDTO);
    }

    /**
     * {@code DELETE  /transaction-fees/:id} : delete the "id" transactionFee.
     *
     * @param id the id of the transactionFeeDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransactionFee(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete TransactionFee : {}", id);
        transactionFeeService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
