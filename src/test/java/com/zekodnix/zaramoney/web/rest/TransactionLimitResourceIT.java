package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.domain.TransactionLimitAsserts.*;
import static com.zekodnix.zaramoney.web.rest.TestUtil.createUpdateProxyForBean;
import static com.zekodnix.zaramoney.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.domain.TransactionLimit;
import com.zekodnix.zaramoney.repository.TransactionLimitRepository;
import com.zekodnix.zaramoney.service.TransactionLimitService;
import com.zekodnix.zaramoney.service.dto.TransactionLimitDTO;
import com.zekodnix.zaramoney.service.mapper.TransactionLimitMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link TransactionLimitResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class TransactionLimitResourceIT {

    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal(1);
    private static final BigDecimal UPDATED_DAILY_LIMIT = new BigDecimal(2);
    private static final BigDecimal SMALLER_DAILY_LIMIT = new BigDecimal(1 - 1);

    private static final BigDecimal DEFAULT_MONTHLY_LIMIT = new BigDecimal(1);
    private static final BigDecimal UPDATED_MONTHLY_LIMIT = new BigDecimal(2);
    private static final BigDecimal SMALLER_MONTHLY_LIMIT = new BigDecimal(1 - 1);

    private static final String ENTITY_API_URL = "/api/transaction-limits";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private TransactionLimitRepository transactionLimitRepository;

    @Mock
    private TransactionLimitRepository transactionLimitRepositoryMock;

    @Autowired
    private TransactionLimitMapper transactionLimitMapper;

    @Mock
    private TransactionLimitService transactionLimitServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restTransactionLimitMockMvc;

    private TransactionLimit transactionLimit;

    private TransactionLimit insertedTransactionLimit;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static TransactionLimit createEntity() {
        return new TransactionLimit().dailyLimit(DEFAULT_DAILY_LIMIT).monthlyLimit(DEFAULT_MONTHLY_LIMIT);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static TransactionLimit createUpdatedEntity() {
        return new TransactionLimit().dailyLimit(UPDATED_DAILY_LIMIT).monthlyLimit(UPDATED_MONTHLY_LIMIT);
    }

    @BeforeEach
    void initTest() {
        transactionLimit = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedTransactionLimit != null) {
            transactionLimitRepository.delete(insertedTransactionLimit);
            insertedTransactionLimit = null;
        }
    }

    @Test
    @Transactional
    void createTransactionLimit() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the TransactionLimit
        TransactionLimitDTO transactionLimitDTO = transactionLimitMapper.toDto(transactionLimit);
        var returnedTransactionLimitDTO = om.readValue(
            restTransactionLimitMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionLimitDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionLimitDTO.class
        );

        // Validate the TransactionLimit in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedTransactionLimit = transactionLimitMapper.toEntity(returnedTransactionLimitDTO);
        assertTransactionLimitUpdatableFieldsEquals(returnedTransactionLimit, getPersistedTransactionLimit(returnedTransactionLimit));

        insertedTransactionLimit = returnedTransactionLimit;
    }

    @Test
    @Transactional
    void createTransactionLimitWithExistingId() throws Exception {
        // Create the TransactionLimit with an existing ID
        transactionLimit.setId(1L);
        TransactionLimitDTO transactionLimitDTO = transactionLimitMapper.toDto(transactionLimit);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restTransactionLimitMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionLimitDTO)))
            .andExpect(status().isBadRequest());

        // Validate the TransactionLimit in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllTransactionLimits() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList
        restTransactionLimitMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionLimit.getId().intValue())))
            .andExpect(jsonPath("$.[*].dailyLimit").value(hasItem(sameNumber(DEFAULT_DAILY_LIMIT))))
            .andExpect(jsonPath("$.[*].monthlyLimit").value(hasItem(sameNumber(DEFAULT_MONTHLY_LIMIT))));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTransactionLimitsWithEagerRelationshipsIsEnabled() throws Exception {
        when(transactionLimitServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTransactionLimitMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(transactionLimitServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTransactionLimitsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(transactionLimitServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTransactionLimitMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(transactionLimitRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getTransactionLimit() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get the transactionLimit
        restTransactionLimitMockMvc
            .perform(get(ENTITY_API_URL_ID, transactionLimit.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(transactionLimit.getId().intValue()))
            .andExpect(jsonPath("$.dailyLimit").value(sameNumber(DEFAULT_DAILY_LIMIT)))
            .andExpect(jsonPath("$.monthlyLimit").value(sameNumber(DEFAULT_MONTHLY_LIMIT)));
    }

    @Test
    @Transactional
    void getTransactionLimitsByIdFiltering() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        Long id = transactionLimit.getId();

        defaultTransactionLimitFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultTransactionLimitFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultTransactionLimitFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByDailyLimitIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where dailyLimit equals to
        defaultTransactionLimitFiltering("dailyLimit.equals=" + DEFAULT_DAILY_LIMIT, "dailyLimit.equals=" + UPDATED_DAILY_LIMIT);
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByDailyLimitIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where dailyLimit in
        defaultTransactionLimitFiltering(
            "dailyLimit.in=" + DEFAULT_DAILY_LIMIT + "," + UPDATED_DAILY_LIMIT,
            "dailyLimit.in=" + UPDATED_DAILY_LIMIT
        );
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByDailyLimitIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where dailyLimit is not null
        defaultTransactionLimitFiltering("dailyLimit.specified=true", "dailyLimit.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByDailyLimitIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where dailyLimit is greater than or equal to
        defaultTransactionLimitFiltering(
            "dailyLimit.greaterThanOrEqual=" + DEFAULT_DAILY_LIMIT,
            "dailyLimit.greaterThanOrEqual=" + UPDATED_DAILY_LIMIT
        );
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByDailyLimitIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where dailyLimit is less than or equal to
        defaultTransactionLimitFiltering(
            "dailyLimit.lessThanOrEqual=" + DEFAULT_DAILY_LIMIT,
            "dailyLimit.lessThanOrEqual=" + SMALLER_DAILY_LIMIT
        );
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByDailyLimitIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where dailyLimit is less than
        defaultTransactionLimitFiltering("dailyLimit.lessThan=" + UPDATED_DAILY_LIMIT, "dailyLimit.lessThan=" + DEFAULT_DAILY_LIMIT);
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByDailyLimitIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where dailyLimit is greater than
        defaultTransactionLimitFiltering("dailyLimit.greaterThan=" + SMALLER_DAILY_LIMIT, "dailyLimit.greaterThan=" + DEFAULT_DAILY_LIMIT);
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByMonthlyLimitIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where monthlyLimit equals to
        defaultTransactionLimitFiltering("monthlyLimit.equals=" + DEFAULT_MONTHLY_LIMIT, "monthlyLimit.equals=" + UPDATED_MONTHLY_LIMIT);
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByMonthlyLimitIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where monthlyLimit in
        defaultTransactionLimitFiltering(
            "monthlyLimit.in=" + DEFAULT_MONTHLY_LIMIT + "," + UPDATED_MONTHLY_LIMIT,
            "monthlyLimit.in=" + UPDATED_MONTHLY_LIMIT
        );
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByMonthlyLimitIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where monthlyLimit is not null
        defaultTransactionLimitFiltering("monthlyLimit.specified=true", "monthlyLimit.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByMonthlyLimitIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where monthlyLimit is greater than or equal to
        defaultTransactionLimitFiltering(
            "monthlyLimit.greaterThanOrEqual=" + DEFAULT_MONTHLY_LIMIT,
            "monthlyLimit.greaterThanOrEqual=" + UPDATED_MONTHLY_LIMIT
        );
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByMonthlyLimitIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where monthlyLimit is less than or equal to
        defaultTransactionLimitFiltering(
            "monthlyLimit.lessThanOrEqual=" + DEFAULT_MONTHLY_LIMIT,
            "monthlyLimit.lessThanOrEqual=" + SMALLER_MONTHLY_LIMIT
        );
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByMonthlyLimitIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where monthlyLimit is less than
        defaultTransactionLimitFiltering(
            "monthlyLimit.lessThan=" + UPDATED_MONTHLY_LIMIT,
            "monthlyLimit.lessThan=" + DEFAULT_MONTHLY_LIMIT
        );
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByMonthlyLimitIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        // Get all the transactionLimitList where monthlyLimit is greater than
        defaultTransactionLimitFiltering(
            "monthlyLimit.greaterThan=" + SMALLER_MONTHLY_LIMIT,
            "monthlyLimit.greaterThan=" + DEFAULT_MONTHLY_LIMIT
        );
    }

    @Test
    @Transactional
    void getAllTransactionLimitsByAccountIsEqualToSomething() throws Exception {
        BankAccount account;
        if (TestUtil.findAll(em, BankAccount.class).isEmpty()) {
            transactionLimitRepository.saveAndFlush(transactionLimit);
            account = BankAccountResourceIT.createEntity();
        } else {
            account = TestUtil.findAll(em, BankAccount.class).get(0);
        }
        em.persist(account);
        em.flush();
        transactionLimit.setAccount(account);
        transactionLimitRepository.saveAndFlush(transactionLimit);
        Long accountId = account.getId();
        // Get all the transactionLimitList where account equals to accountId
        defaultTransactionLimitShouldBeFound("accountId.equals=" + accountId);

        // Get all the transactionLimitList where account equals to (accountId + 1)
        defaultTransactionLimitShouldNotBeFound("accountId.equals=" + (accountId + 1));
    }

    private void defaultTransactionLimitFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultTransactionLimitShouldBeFound(shouldBeFound);
        defaultTransactionLimitShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultTransactionLimitShouldBeFound(String filter) throws Exception {
        restTransactionLimitMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionLimit.getId().intValue())))
            .andExpect(jsonPath("$.[*].dailyLimit").value(hasItem(sameNumber(DEFAULT_DAILY_LIMIT))))
            .andExpect(jsonPath("$.[*].monthlyLimit").value(hasItem(sameNumber(DEFAULT_MONTHLY_LIMIT))));

        // Check, that the count call also returns 1
        restTransactionLimitMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultTransactionLimitShouldNotBeFound(String filter) throws Exception {
        restTransactionLimitMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restTransactionLimitMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingTransactionLimit() throws Exception {
        // Get the transactionLimit
        restTransactionLimitMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingTransactionLimit() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionLimit
        TransactionLimit updatedTransactionLimit = transactionLimitRepository.findById(transactionLimit.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedTransactionLimit are not directly saved in db
        em.detach(updatedTransactionLimit);
        updatedTransactionLimit.dailyLimit(UPDATED_DAILY_LIMIT).monthlyLimit(UPDATED_MONTHLY_LIMIT);
        TransactionLimitDTO transactionLimitDTO = transactionLimitMapper.toDto(updatedTransactionLimit);

        restTransactionLimitMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionLimitDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionLimitDTO))
            )
            .andExpect(status().isOk());

        // Validate the TransactionLimit in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedTransactionLimitToMatchAllProperties(updatedTransactionLimit);
    }

    @Test
    @Transactional
    void putNonExistingTransactionLimit() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionLimit.setId(longCount.incrementAndGet());

        // Create the TransactionLimit
        TransactionLimitDTO transactionLimitDTO = transactionLimitMapper.toDto(transactionLimit);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTransactionLimitMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionLimitDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionLimitDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionLimit in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchTransactionLimit() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionLimit.setId(longCount.incrementAndGet());

        // Create the TransactionLimit
        TransactionLimitDTO transactionLimitDTO = transactionLimitMapper.toDto(transactionLimit);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionLimitMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionLimitDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionLimit in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamTransactionLimit() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionLimit.setId(longCount.incrementAndGet());

        // Create the TransactionLimit
        TransactionLimitDTO transactionLimitDTO = transactionLimitMapper.toDto(transactionLimit);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionLimitMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionLimitDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the TransactionLimit in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateTransactionLimitWithPatch() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionLimit using partial update
        TransactionLimit partialUpdatedTransactionLimit = new TransactionLimit();
        partialUpdatedTransactionLimit.setId(transactionLimit.getId());

        restTransactionLimitMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTransactionLimit.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTransactionLimit))
            )
            .andExpect(status().isOk());

        // Validate the TransactionLimit in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTransactionLimitUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedTransactionLimit, transactionLimit),
            getPersistedTransactionLimit(transactionLimit)
        );
    }

    @Test
    @Transactional
    void fullUpdateTransactionLimitWithPatch() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionLimit using partial update
        TransactionLimit partialUpdatedTransactionLimit = new TransactionLimit();
        partialUpdatedTransactionLimit.setId(transactionLimit.getId());

        partialUpdatedTransactionLimit.dailyLimit(UPDATED_DAILY_LIMIT).monthlyLimit(UPDATED_MONTHLY_LIMIT);

        restTransactionLimitMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTransactionLimit.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTransactionLimit))
            )
            .andExpect(status().isOk());

        // Validate the TransactionLimit in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTransactionLimitUpdatableFieldsEquals(
            partialUpdatedTransactionLimit,
            getPersistedTransactionLimit(partialUpdatedTransactionLimit)
        );
    }

    @Test
    @Transactional
    void patchNonExistingTransactionLimit() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionLimit.setId(longCount.incrementAndGet());

        // Create the TransactionLimit
        TransactionLimitDTO transactionLimitDTO = transactionLimitMapper.toDto(transactionLimit);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTransactionLimitMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, transactionLimitDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionLimitDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionLimit in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchTransactionLimit() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionLimit.setId(longCount.incrementAndGet());

        // Create the TransactionLimit
        TransactionLimitDTO transactionLimitDTO = transactionLimitMapper.toDto(transactionLimit);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionLimitMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionLimitDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionLimit in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamTransactionLimit() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionLimit.setId(longCount.incrementAndGet());

        // Create the TransactionLimit
        TransactionLimitDTO transactionLimitDTO = transactionLimitMapper.toDto(transactionLimit);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionLimitMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(transactionLimitDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the TransactionLimit in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteTransactionLimit() throws Exception {
        // Initialize the database
        insertedTransactionLimit = transactionLimitRepository.saveAndFlush(transactionLimit);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the transactionLimit
        restTransactionLimitMockMvc
            .perform(delete(ENTITY_API_URL_ID, transactionLimit.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return transactionLimitRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected TransactionLimit getPersistedTransactionLimit(TransactionLimit transactionLimit) {
        return transactionLimitRepository.findById(transactionLimit.getId()).orElseThrow();
    }

    protected void assertPersistedTransactionLimitToMatchAllProperties(TransactionLimit expectedTransactionLimit) {
        assertTransactionLimitAllPropertiesEquals(expectedTransactionLimit, getPersistedTransactionLimit(expectedTransactionLimit));
    }

    protected void assertPersistedTransactionLimitToMatchUpdatableProperties(TransactionLimit expectedTransactionLimit) {
        assertTransactionLimitAllUpdatablePropertiesEquals(
            expectedTransactionLimit,
            getPersistedTransactionLimit(expectedTransactionLimit)
        );
    }
}
