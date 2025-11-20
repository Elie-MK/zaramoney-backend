package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.service.UserDetailsAccountQueryService;
import com.zekodnix.zaramoney.service.UserDetailsAccountService;
import com.zekodnix.zaramoney.service.criteria.UserDetailsAccountCriteria;
import com.zekodnix.zaramoney.service.dto.UserDetailsAccountDTO;
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
 * REST controller for managing {@link com.zekodnix.zaramoney.domain.UserDetailsAccount}.
 */
@RestController
@RequestMapping("/api/user-details-accounts")
public class UserDetailsAccountResource {

    private static final Logger LOG = LoggerFactory.getLogger(UserDetailsAccountResource.class);

    private static final String ENTITY_NAME = "userDetailsAccount";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserDetailsAccountService userDetailsAccountService;

    private final UserDetailsAccountRepository userDetailsAccountRepository;

    private final UserDetailsAccountQueryService userDetailsAccountQueryService;

    public UserDetailsAccountResource(
        UserDetailsAccountService userDetailsAccountService,
        UserDetailsAccountRepository userDetailsAccountRepository,
        UserDetailsAccountQueryService userDetailsAccountQueryService
    ) {
        this.userDetailsAccountService = userDetailsAccountService;
        this.userDetailsAccountRepository = userDetailsAccountRepository;
        this.userDetailsAccountQueryService = userDetailsAccountQueryService;
    }

    /**
     * {@code POST  /user-details-accounts} : Create a new userDetailsAccount.
     *
     * @param userDetailsAccountDTO the userDetailsAccountDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new userDetailsAccountDTO, or with status {@code 400 (Bad Request)} if the userDetailsAccount has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<UserDetailsAccountDTO> createUserDetailsAccount(@Valid @RequestBody UserDetailsAccountDTO userDetailsAccountDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save UserDetailsAccount : {}", userDetailsAccountDTO);
        if (userDetailsAccountDTO.getId() != null) {
            throw new BadRequestAlertException("A new userDetailsAccount cannot already have an ID", ENTITY_NAME, "idexists");
        }
        userDetailsAccountDTO = userDetailsAccountService.save(userDetailsAccountDTO);
        return ResponseEntity.created(new URI("/api/user-details-accounts/" + userDetailsAccountDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, userDetailsAccountDTO.getId().toString()))
            .body(userDetailsAccountDTO);
    }

    /**
     * {@code PUT  /user-details-accounts/:id} : Updates an existing userDetailsAccount.
     *
     * @param id the id of the userDetailsAccountDTO to save.
     * @param userDetailsAccountDTO the userDetailsAccountDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated userDetailsAccountDTO,
     * or with status {@code 400 (Bad Request)} if the userDetailsAccountDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the userDetailsAccountDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDetailsAccountDTO> updateUserDetailsAccount(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody UserDetailsAccountDTO userDetailsAccountDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update UserDetailsAccount : {}, {}", id, userDetailsAccountDTO);
        if (userDetailsAccountDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, userDetailsAccountDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!userDetailsAccountRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        userDetailsAccountDTO = userDetailsAccountService.update(userDetailsAccountDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, userDetailsAccountDTO.getId().toString()))
            .body(userDetailsAccountDTO);
    }

    /**
     * {@code PATCH  /user-details-accounts/:id} : Partial updates given fields of an existing userDetailsAccount, field will ignore if it is null
     *
     * @param id the id of the userDetailsAccountDTO to save.
     * @param userDetailsAccountDTO the userDetailsAccountDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated userDetailsAccountDTO,
     * or with status {@code 400 (Bad Request)} if the userDetailsAccountDTO is not valid,
     * or with status {@code 404 (Not Found)} if the userDetailsAccountDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the userDetailsAccountDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<UserDetailsAccountDTO> partialUpdateUserDetailsAccount(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody UserDetailsAccountDTO userDetailsAccountDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update UserDetailsAccount partially : {}, {}", id, userDetailsAccountDTO);
        if (userDetailsAccountDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, userDetailsAccountDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!userDetailsAccountRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<UserDetailsAccountDTO> result = userDetailsAccountService.partialUpdate(userDetailsAccountDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, userDetailsAccountDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /user-details-accounts} : get all the userDetailsAccounts.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of userDetailsAccounts in body.
     */
    @GetMapping("")
    public ResponseEntity<List<UserDetailsAccountDTO>> getAllUserDetailsAccounts(
        UserDetailsAccountCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get UserDetailsAccounts by criteria: {}", criteria);

        Page<UserDetailsAccountDTO> page = userDetailsAccountQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /user-details-accounts/count} : count all the userDetailsAccounts.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countUserDetailsAccounts(UserDetailsAccountCriteria criteria) {
        LOG.debug("REST request to count UserDetailsAccounts by criteria: {}", criteria);
        return ResponseEntity.ok().body(userDetailsAccountQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /user-details-accounts/:id} : get the "id" userDetailsAccount.
     *
     * @param id the id of the userDetailsAccountDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the userDetailsAccountDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDetailsAccountDTO> getUserDetailsAccount(@PathVariable("id") Long id) {
        LOG.debug("REST request to get UserDetailsAccount : {}", id);
        Optional<UserDetailsAccountDTO> userDetailsAccountDTO = userDetailsAccountService.findOne(id);
        return ResponseUtil.wrapOrNotFound(userDetailsAccountDTO);
    }

    /**
     * {@code DELETE  /user-details-accounts/:id} : delete the "id" userDetailsAccount.
     *
     * @param id the id of the userDetailsAccountDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUserDetailsAccount(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete UserDetailsAccount : {}", id);
        userDetailsAccountService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
