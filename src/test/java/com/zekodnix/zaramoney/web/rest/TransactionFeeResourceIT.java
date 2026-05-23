package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.domain.TransactionFeeAsserts.*;
import static com.zekodnix.zaramoney.web.rest.TestUtil.createUpdateProxyForBean;
import static com.zekodnix.zaramoney.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.domain.TransactionFee;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.repository.TransactionFeeRepository;
import com.zekodnix.zaramoney.service.TransactionFeeService;
import com.zekodnix.zaramoney.service.dto.TransactionFeeDTO;
import com.zekodnix.zaramoney.service.mapper.TransactionFeeMapper;
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
 * Integration tests for the {@link TransactionFeeResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class TransactionFeeResourceIT {

    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal(1);
    private static final BigDecimal UPDATED_AMOUNT = new BigDecimal(2);
    private static final BigDecimal SMALLER_AMOUNT = new BigDecimal(1 - 1);

    private static final Currency DEFAULT_CURRENCY = Currency.USD;
    private static final Currency UPDATED_CURRENCY = Currency.TND;

    private static final String DEFAULT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_TYPE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/transaction-fees";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private TransactionFeeRepository transactionFeeRepository;

    @Mock
    private TransactionFeeRepository transactionFeeRepositoryMock;

    @Autowired
    private TransactionFeeMapper transactionFeeMapper;

    @Mock
    private TransactionFeeService transactionFeeServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restTransactionFeeMockMvc;

    private TransactionFee transactionFee;

    private TransactionFee insertedTransactionFee;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static TransactionFee createEntity() {
        return new TransactionFee().amount(DEFAULT_AMOUNT).currency(DEFAULT_CURRENCY).type(DEFAULT_TYPE);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static TransactionFee createUpdatedEntity() {
        return new TransactionFee().amount(UPDATED_AMOUNT).currency(UPDATED_CURRENCY).type(UPDATED_TYPE);
    }

    @BeforeEach
    void initTest() {
        transactionFee = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedTransactionFee != null) {
            transactionFeeRepository.delete(insertedTransactionFee);
            insertedTransactionFee = null;
        }
    }

    @Test
    @Transactional
    void createTransactionFee() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the TransactionFee
        TransactionFeeDTO transactionFeeDTO = transactionFeeMapper.toDto(transactionFee);
        var returnedTransactionFeeDTO = om.readValue(
            restTransactionFeeMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionFeeDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionFeeDTO.class
        );

        // Validate the TransactionFee in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedTransactionFee = transactionFeeMapper.toEntity(returnedTransactionFeeDTO);
        assertTransactionFeeUpdatableFieldsEquals(returnedTransactionFee, getPersistedTransactionFee(returnedTransactionFee));

        insertedTransactionFee = returnedTransactionFee;
    }

    @Test
    @Transactional
    void createTransactionFeeWithExistingId() throws Exception {
        // Create the TransactionFee with an existing ID
        transactionFee.setId(1L);
        TransactionFeeDTO transactionFeeDTO = transactionFeeMapper.toDto(transactionFee);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restTransactionFeeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionFeeDTO)))
            .andExpect(status().isBadRequest());

        // Validate the TransactionFee in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkAmountIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionFee.setAmount(null);

        // Create the TransactionFee, which fails.
        TransactionFeeDTO transactionFeeDTO = transactionFeeMapper.toDto(transactionFee);

        restTransactionFeeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionFeeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionFee.setCurrency(null);

        // Create the TransactionFee, which fails.
        TransactionFeeDTO transactionFeeDTO = transactionFeeMapper.toDto(transactionFee);

        restTransactionFeeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionFeeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionFee.setType(null);

        // Create the TransactionFee, which fails.
        TransactionFeeDTO transactionFeeDTO = transactionFeeMapper.toDto(transactionFee);

        restTransactionFeeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionFeeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllTransactionFees() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList
        restTransactionFeeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionFee.getId().intValue())))
            .andExpect(jsonPath("$.[*].amount").value(hasItem(sameNumber(DEFAULT_AMOUNT))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY.toString())))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTransactionFeesWithEagerRelationshipsIsEnabled() throws Exception {
        when(transactionFeeServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTransactionFeeMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(transactionFeeServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTransactionFeesWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(transactionFeeServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTransactionFeeMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(transactionFeeRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getTransactionFee() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get the transactionFee
        restTransactionFeeMockMvc
            .perform(get(ENTITY_API_URL_ID, transactionFee.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(transactionFee.getId().intValue()))
            .andExpect(jsonPath("$.amount").value(sameNumber(DEFAULT_AMOUNT)))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY.toString()))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE));
    }

    @Test
    @Transactional
    void getTransactionFeesByIdFiltering() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        Long id = transactionFee.getId();

        defaultTransactionFeeFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultTransactionFeeFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultTransactionFeeFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByAmountIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where amount equals to
        defaultTransactionFeeFiltering("amount.equals=" + DEFAULT_AMOUNT, "amount.equals=" + UPDATED_AMOUNT);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByAmountIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where amount in
        defaultTransactionFeeFiltering("amount.in=" + DEFAULT_AMOUNT + "," + UPDATED_AMOUNT, "amount.in=" + UPDATED_AMOUNT);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByAmountIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where amount is not null
        defaultTransactionFeeFiltering("amount.specified=true", "amount.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionFeesByAmountIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where amount is greater than or equal to
        defaultTransactionFeeFiltering("amount.greaterThanOrEqual=" + DEFAULT_AMOUNT, "amount.greaterThanOrEqual=" + UPDATED_AMOUNT);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByAmountIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where amount is less than or equal to
        defaultTransactionFeeFiltering("amount.lessThanOrEqual=" + DEFAULT_AMOUNT, "amount.lessThanOrEqual=" + SMALLER_AMOUNT);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByAmountIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where amount is less than
        defaultTransactionFeeFiltering("amount.lessThan=" + UPDATED_AMOUNT, "amount.lessThan=" + DEFAULT_AMOUNT);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByAmountIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where amount is greater than
        defaultTransactionFeeFiltering("amount.greaterThan=" + SMALLER_AMOUNT, "amount.greaterThan=" + DEFAULT_AMOUNT);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByCurrencyIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where currency equals to
        defaultTransactionFeeFiltering("currency.equals=" + DEFAULT_CURRENCY, "currency.equals=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByCurrencyIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where currency in
        defaultTransactionFeeFiltering("currency.in=" + DEFAULT_CURRENCY + "," + UPDATED_CURRENCY, "currency.in=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByCurrencyIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where currency is not null
        defaultTransactionFeeFiltering("currency.specified=true", "currency.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionFeesByTypeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where type equals to
        defaultTransactionFeeFiltering("type.equals=" + DEFAULT_TYPE, "type.equals=" + UPDATED_TYPE);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByTypeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where type in
        defaultTransactionFeeFiltering("type.in=" + DEFAULT_TYPE + "," + UPDATED_TYPE, "type.in=" + UPDATED_TYPE);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByTypeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where type is not null
        defaultTransactionFeeFiltering("type.specified=true", "type.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionFeesByTypeContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where type contains
        defaultTransactionFeeFiltering("type.contains=" + DEFAULT_TYPE, "type.contains=" + UPDATED_TYPE);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByTypeNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        // Get all the transactionFeeList where type does not contain
        defaultTransactionFeeFiltering("type.doesNotContain=" + UPDATED_TYPE, "type.doesNotContain=" + DEFAULT_TYPE);
    }

    @Test
    @Transactional
    void getAllTransactionFeesByTransactionIsEqualToSomething() throws Exception {
        TransactionRecord transaction;
        if (TestUtil.findAll(em, TransactionRecord.class).isEmpty()) {
            transactionFeeRepository.saveAndFlush(transactionFee);
            transaction = TransactionRecordResourceIT.createEntity();
        } else {
            transaction = TestUtil.findAll(em, TransactionRecord.class).get(0);
        }
        em.persist(transaction);
        em.flush();
        transactionFee.setTransaction(transaction);
        transactionFeeRepository.saveAndFlush(transactionFee);
        Long transactionId = transaction.getId();
        // Get all the transactionFeeList where transaction equals to transactionId
        defaultTransactionFeeShouldBeFound("transactionId.equals=" + transactionId);

        // Get all the transactionFeeList where transaction equals to (transactionId + 1)
        defaultTransactionFeeShouldNotBeFound("transactionId.equals=" + (transactionId + 1));
    }

    private void defaultTransactionFeeFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultTransactionFeeShouldBeFound(shouldBeFound);
        defaultTransactionFeeShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultTransactionFeeShouldBeFound(String filter) throws Exception {
        restTransactionFeeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionFee.getId().intValue())))
            .andExpect(jsonPath("$.[*].amount").value(hasItem(sameNumber(DEFAULT_AMOUNT))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY.toString())))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)));

        // Check, that the count call also returns 1
        restTransactionFeeMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultTransactionFeeShouldNotBeFound(String filter) throws Exception {
        restTransactionFeeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restTransactionFeeMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingTransactionFee() throws Exception {
        // Get the transactionFee
        restTransactionFeeMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingTransactionFee() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionFee
        TransactionFee updatedTransactionFee = transactionFeeRepository.findById(transactionFee.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedTransactionFee are not directly saved in db
        em.detach(updatedTransactionFee);
        updatedTransactionFee.amount(UPDATED_AMOUNT).currency(UPDATED_CURRENCY).type(UPDATED_TYPE);
        TransactionFeeDTO transactionFeeDTO = transactionFeeMapper.toDto(updatedTransactionFee);

        restTransactionFeeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionFeeDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionFeeDTO))
            )
            .andExpect(status().isOk());

        // Validate the TransactionFee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedTransactionFeeToMatchAllProperties(updatedTransactionFee);
    }

    @Test
    @Transactional
    void putNonExistingTransactionFee() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionFee.setId(longCount.incrementAndGet());

        // Create the TransactionFee
        TransactionFeeDTO transactionFeeDTO = transactionFeeMapper.toDto(transactionFee);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTransactionFeeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionFeeDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionFeeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionFee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchTransactionFee() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionFee.setId(longCount.incrementAndGet());

        // Create the TransactionFee
        TransactionFeeDTO transactionFeeDTO = transactionFeeMapper.toDto(transactionFee);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionFeeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionFeeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionFee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamTransactionFee() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionFee.setId(longCount.incrementAndGet());

        // Create the TransactionFee
        TransactionFeeDTO transactionFeeDTO = transactionFeeMapper.toDto(transactionFee);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionFeeMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionFeeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the TransactionFee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateTransactionFeeWithPatch() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionFee using partial update
        TransactionFee partialUpdatedTransactionFee = new TransactionFee();
        partialUpdatedTransactionFee.setId(transactionFee.getId());

        partialUpdatedTransactionFee.amount(UPDATED_AMOUNT);

        restTransactionFeeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTransactionFee.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTransactionFee))
            )
            .andExpect(status().isOk());

        // Validate the TransactionFee in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTransactionFeeUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedTransactionFee, transactionFee),
            getPersistedTransactionFee(transactionFee)
        );
    }

    @Test
    @Transactional
    void fullUpdateTransactionFeeWithPatch() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionFee using partial update
        TransactionFee partialUpdatedTransactionFee = new TransactionFee();
        partialUpdatedTransactionFee.setId(transactionFee.getId());

        partialUpdatedTransactionFee.amount(UPDATED_AMOUNT).currency(UPDATED_CURRENCY).type(UPDATED_TYPE);

        restTransactionFeeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTransactionFee.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTransactionFee))
            )
            .andExpect(status().isOk());

        // Validate the TransactionFee in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTransactionFeeUpdatableFieldsEquals(partialUpdatedTransactionFee, getPersistedTransactionFee(partialUpdatedTransactionFee));
    }

    @Test
    @Transactional
    void patchNonExistingTransactionFee() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionFee.setId(longCount.incrementAndGet());

        // Create the TransactionFee
        TransactionFeeDTO transactionFeeDTO = transactionFeeMapper.toDto(transactionFee);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTransactionFeeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, transactionFeeDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionFeeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionFee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchTransactionFee() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionFee.setId(longCount.incrementAndGet());

        // Create the TransactionFee
        TransactionFeeDTO transactionFeeDTO = transactionFeeMapper.toDto(transactionFee);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionFeeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionFeeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionFee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamTransactionFee() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionFee.setId(longCount.incrementAndGet());

        // Create the TransactionFee
        TransactionFeeDTO transactionFeeDTO = transactionFeeMapper.toDto(transactionFee);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionFeeMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(transactionFeeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the TransactionFee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteTransactionFee() throws Exception {
        // Initialize the database
        insertedTransactionFee = transactionFeeRepository.saveAndFlush(transactionFee);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the transactionFee
        restTransactionFeeMockMvc
            .perform(delete(ENTITY_API_URL_ID, transactionFee.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return transactionFeeRepository.count();
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

    protected TransactionFee getPersistedTransactionFee(TransactionFee transactionFee) {
        return transactionFeeRepository.findById(transactionFee.getId()).orElseThrow();
    }

    protected void assertPersistedTransactionFeeToMatchAllProperties(TransactionFee expectedTransactionFee) {
        assertTransactionFeeAllPropertiesEquals(expectedTransactionFee, getPersistedTransactionFee(expectedTransactionFee));
    }

    protected void assertPersistedTransactionFeeToMatchUpdatableProperties(TransactionFee expectedTransactionFee) {
        assertTransactionFeeAllUpdatablePropertiesEquals(expectedTransactionFee, getPersistedTransactionFee(expectedTransactionFee));
    }
}
