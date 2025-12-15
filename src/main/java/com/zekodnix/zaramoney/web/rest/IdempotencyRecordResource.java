package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.repository.IdempotencyRecordRepository;
import com.zekodnix.zaramoney.service.IdempotencyRecordQueryService;
import com.zekodnix.zaramoney.service.IdempotencyRecordService;
import com.zekodnix.zaramoney.service.criteria.IdempotencyRecordCriteria;
import com.zekodnix.zaramoney.service.dto.IdempotencyRecordDTO;
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
 * REST controller for managing {@link com.zekodnix.zaramoney.domain.IdempotencyRecord}.
 */
@RestController
@RequestMapping("/api/idempotency-records")
public class IdempotencyRecordResource {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyRecordResource.class);

    private static final String ENTITY_NAME = "idempotencyRecord";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final IdempotencyRecordService idempotencyRecordService;

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    private final IdempotencyRecordQueryService idempotencyRecordQueryService;

    public IdempotencyRecordResource(
        IdempotencyRecordService idempotencyRecordService,
        IdempotencyRecordRepository idempotencyRecordRepository,
        IdempotencyRecordQueryService idempotencyRecordQueryService
    ) {
        this.idempotencyRecordService = idempotencyRecordService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.idempotencyRecordQueryService = idempotencyRecordQueryService;
    }

    /**
     * {@code POST  /idempotency-records} : Create a new idempotencyRecord.
     *
     * @param idempotencyRecordDTO the idempotencyRecordDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new idempotencyRecordDTO, or with status {@code 400 (Bad Request)} if the idempotencyRecord has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<IdempotencyRecordDTO> createIdempotencyRecord(@Valid @RequestBody IdempotencyRecordDTO idempotencyRecordDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save IdempotencyRecord : {}", idempotencyRecordDTO);
        if (idempotencyRecordDTO.getId() != null) {
            throw new BadRequestAlertException("A new idempotencyRecord cannot already have an ID", ENTITY_NAME, "idexists");
        }
        idempotencyRecordDTO = idempotencyRecordService.save(idempotencyRecordDTO);
        return ResponseEntity.created(new URI("/api/idempotency-records/" + idempotencyRecordDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, idempotencyRecordDTO.getId().toString()))
            .body(idempotencyRecordDTO);
    }

    /**
     * {@code PUT  /idempotency-records/:id} : Updates an existing idempotencyRecord.
     *
     * @param id the id of the idempotencyRecordDTO to save.
     * @param idempotencyRecordDTO the idempotencyRecordDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated idempotencyRecordDTO,
     * or with status {@code 400 (Bad Request)} if the idempotencyRecordDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the idempotencyRecordDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<IdempotencyRecordDTO> updateIdempotencyRecord(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody IdempotencyRecordDTO idempotencyRecordDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update IdempotencyRecord : {}, {}", id, idempotencyRecordDTO);
        if (idempotencyRecordDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, idempotencyRecordDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!idempotencyRecordRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        idempotencyRecordDTO = idempotencyRecordService.update(idempotencyRecordDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, idempotencyRecordDTO.getId().toString()))
            .body(idempotencyRecordDTO);
    }

    /**
     * {@code PATCH  /idempotency-records/:id} : Partial updates given fields of an existing idempotencyRecord, field will ignore if it is null
     *
     * @param id the id of the idempotencyRecordDTO to save.
     * @param idempotencyRecordDTO the idempotencyRecordDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated idempotencyRecordDTO,
     * or with status {@code 400 (Bad Request)} if the idempotencyRecordDTO is not valid,
     * or with status {@code 404 (Not Found)} if the idempotencyRecordDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the idempotencyRecordDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<IdempotencyRecordDTO> partialUpdateIdempotencyRecord(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody IdempotencyRecordDTO idempotencyRecordDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update IdempotencyRecord partially : {}, {}", id, idempotencyRecordDTO);
        if (idempotencyRecordDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, idempotencyRecordDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!idempotencyRecordRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<IdempotencyRecordDTO> result = idempotencyRecordService.partialUpdate(idempotencyRecordDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, idempotencyRecordDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /idempotency-records} : get all the idempotencyRecords.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of idempotencyRecords in body.
     */
    @GetMapping("")
    public ResponseEntity<List<IdempotencyRecordDTO>> getAllIdempotencyRecords(
        IdempotencyRecordCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get IdempotencyRecords by criteria: {}", criteria);

        Page<IdempotencyRecordDTO> page = idempotencyRecordQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /idempotency-records/count} : count all the idempotencyRecords.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countIdempotencyRecords(IdempotencyRecordCriteria criteria) {
        LOG.debug("REST request to count IdempotencyRecords by criteria: {}", criteria);
        return ResponseEntity.ok().body(idempotencyRecordQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /idempotency-records/:id} : get the "id" idempotencyRecord.
     *
     * @param id the id of the idempotencyRecordDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the idempotencyRecordDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<IdempotencyRecordDTO> getIdempotencyRecord(@PathVariable("id") Long id) {
        LOG.debug("REST request to get IdempotencyRecord : {}", id);
        Optional<IdempotencyRecordDTO> idempotencyRecordDTO = idempotencyRecordService.findOne(id);
        return ResponseUtil.wrapOrNotFound(idempotencyRecordDTO);
    }

    /**
     * {@code DELETE  /idempotency-records/:id} : delete the "id" idempotencyRecord.
     *
     * @param id the id of the idempotencyRecordDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIdempotencyRecord(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete IdempotencyRecord : {}", id);
        idempotencyRecordService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
