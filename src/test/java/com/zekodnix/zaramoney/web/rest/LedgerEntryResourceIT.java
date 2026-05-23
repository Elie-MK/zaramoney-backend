package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.domain.LedgerEntryAsserts.*;
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
import com.zekodnix.zaramoney.domain.LedgerEntry;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.EntryType;
import com.zekodnix.zaramoney.repository.LedgerEntryRepository;
import com.zekodnix.zaramoney.service.LedgerEntryService;
import com.zekodnix.zaramoney.service.dto.LedgerEntryDTO;
import com.zekodnix.zaramoney.service.mapper.LedgerEntryMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Integration tests for the {@link LedgerEntryResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class LedgerEntryResourceIT {

    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal(1);
    private static final BigDecimal UPDATED_AMOUNT = new BigDecimal(2);
    private static final BigDecimal SMALLER_AMOUNT = new BigDecimal(1 - 1);

    private static final Currency DEFAULT_CURRENCY = Currency.USD;
    private static final Currency UPDATED_CURRENCY = Currency.TND;

    private static final EntryType DEFAULT_ENTRY_TYPE = EntryType.DEBIT;
    private static final EntryType UPDATED_ENTRY_TYPE = EntryType.CREDIT;

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/ledger-entries";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepositoryMock;

    @Autowired
    private LedgerEntryMapper ledgerEntryMapper;

    @Mock
    private LedgerEntryService ledgerEntryServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restLedgerEntryMockMvc;

    private LedgerEntry ledgerEntry;

    private LedgerEntry insertedLedgerEntry;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static LedgerEntry createEntity() {
        return new LedgerEntry()
            .amount(DEFAULT_AMOUNT)
            .currency(DEFAULT_CURRENCY)
            .entryType(DEFAULT_ENTRY_TYPE)
            .createdAt(DEFAULT_CREATED_AT);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static LedgerEntry createUpdatedEntity() {
        return new LedgerEntry()
            .amount(UPDATED_AMOUNT)
            .currency(UPDATED_CURRENCY)
            .entryType(UPDATED_ENTRY_TYPE)
            .createdAt(UPDATED_CREATED_AT);
    }

    @BeforeEach
    void initTest() {
        ledgerEntry = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedLedgerEntry != null) {
            ledgerEntryRepository.delete(insertedLedgerEntry);
            insertedLedgerEntry = null;
        }
    }

    @Test
    @Transactional
    void createLedgerEntry() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the LedgerEntry
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(ledgerEntry);
        var returnedLedgerEntryDTO = om.readValue(
            restLedgerEntryMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerEntryDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            LedgerEntryDTO.class
        );

        // Validate the LedgerEntry in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedLedgerEntry = ledgerEntryMapper.toEntity(returnedLedgerEntryDTO);
        assertLedgerEntryUpdatableFieldsEquals(returnedLedgerEntry, getPersistedLedgerEntry(returnedLedgerEntry));

        insertedLedgerEntry = returnedLedgerEntry;
    }

    @Test
    @Transactional
    void createLedgerEntryWithExistingId() throws Exception {
        // Create the LedgerEntry with an existing ID
        ledgerEntry.setId(1L);
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(ledgerEntry);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restLedgerEntryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerEntryDTO)))
            .andExpect(status().isBadRequest());

        // Validate the LedgerEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkAmountIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledgerEntry.setAmount(null);

        // Create the LedgerEntry, which fails.
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(ledgerEntry);

        restLedgerEntryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerEntryDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledgerEntry.setCurrency(null);

        // Create the LedgerEntry, which fails.
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(ledgerEntry);

        restLedgerEntryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerEntryDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkEntryTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledgerEntry.setEntryType(null);

        // Create the LedgerEntry, which fails.
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(ledgerEntry);

        restLedgerEntryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerEntryDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledgerEntry.setCreatedAt(null);

        // Create the LedgerEntry, which fails.
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(ledgerEntry);

        restLedgerEntryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerEntryDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllLedgerEntries() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList
        restLedgerEntryMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(ledgerEntry.getId().intValue())))
            .andExpect(jsonPath("$.[*].amount").value(hasItem(sameNumber(DEFAULT_AMOUNT))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY.toString())))
            .andExpect(jsonPath("$.[*].entryType").value(hasItem(DEFAULT_ENTRY_TYPE.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllLedgerEntriesWithEagerRelationshipsIsEnabled() throws Exception {
        when(ledgerEntryServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restLedgerEntryMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(ledgerEntryServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllLedgerEntriesWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(ledgerEntryServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restLedgerEntryMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(ledgerEntryRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getLedgerEntry() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get the ledgerEntry
        restLedgerEntryMockMvc
            .perform(get(ENTITY_API_URL_ID, ledgerEntry.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(ledgerEntry.getId().intValue()))
            .andExpect(jsonPath("$.amount").value(sameNumber(DEFAULT_AMOUNT)))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY.toString()))
            .andExpect(jsonPath("$.entryType").value(DEFAULT_ENTRY_TYPE.toString()))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()));
    }

    @Test
    @Transactional
    void getLedgerEntriesByIdFiltering() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        Long id = ledgerEntry.getId();

        defaultLedgerEntryFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultLedgerEntryFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultLedgerEntryFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByAmountIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where amount equals to
        defaultLedgerEntryFiltering("amount.equals=" + DEFAULT_AMOUNT, "amount.equals=" + UPDATED_AMOUNT);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByAmountIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where amount in
        defaultLedgerEntryFiltering("amount.in=" + DEFAULT_AMOUNT + "," + UPDATED_AMOUNT, "amount.in=" + UPDATED_AMOUNT);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByAmountIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where amount is not null
        defaultLedgerEntryFiltering("amount.specified=true", "amount.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByAmountIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where amount is greater than or equal to
        defaultLedgerEntryFiltering("amount.greaterThanOrEqual=" + DEFAULT_AMOUNT, "amount.greaterThanOrEqual=" + UPDATED_AMOUNT);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByAmountIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where amount is less than or equal to
        defaultLedgerEntryFiltering("amount.lessThanOrEqual=" + DEFAULT_AMOUNT, "amount.lessThanOrEqual=" + SMALLER_AMOUNT);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByAmountIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where amount is less than
        defaultLedgerEntryFiltering("amount.lessThan=" + UPDATED_AMOUNT, "amount.lessThan=" + DEFAULT_AMOUNT);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByAmountIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where amount is greater than
        defaultLedgerEntryFiltering("amount.greaterThan=" + SMALLER_AMOUNT, "amount.greaterThan=" + DEFAULT_AMOUNT);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByCurrencyIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where currency equals to
        defaultLedgerEntryFiltering("currency.equals=" + DEFAULT_CURRENCY, "currency.equals=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByCurrencyIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where currency in
        defaultLedgerEntryFiltering("currency.in=" + DEFAULT_CURRENCY + "," + UPDATED_CURRENCY, "currency.in=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByCurrencyIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where currency is not null
        defaultLedgerEntryFiltering("currency.specified=true", "currency.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByEntryTypeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where entryType equals to
        defaultLedgerEntryFiltering("entryType.equals=" + DEFAULT_ENTRY_TYPE, "entryType.equals=" + UPDATED_ENTRY_TYPE);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByEntryTypeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where entryType in
        defaultLedgerEntryFiltering("entryType.in=" + DEFAULT_ENTRY_TYPE + "," + UPDATED_ENTRY_TYPE, "entryType.in=" + UPDATED_ENTRY_TYPE);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByEntryTypeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where entryType is not null
        defaultLedgerEntryFiltering("entryType.specified=true", "entryType.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where createdAt equals to
        defaultLedgerEntryFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where createdAt in
        defaultLedgerEntryFiltering("createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT, "createdAt.in=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        // Get all the ledgerEntryList where createdAt is not null
        defaultLedgerEntryFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByAccountIsEqualToSomething() throws Exception {
        BankAccount account;
        if (TestUtil.findAll(em, BankAccount.class).isEmpty()) {
            ledgerEntryRepository.saveAndFlush(ledgerEntry);
            account = BankAccountResourceIT.createEntity();
        } else {
            account = TestUtil.findAll(em, BankAccount.class).get(0);
        }
        em.persist(account);
        em.flush();
        ledgerEntry.setAccount(account);
        ledgerEntryRepository.saveAndFlush(ledgerEntry);
        Long accountId = account.getId();
        // Get all the ledgerEntryList where account equals to accountId
        defaultLedgerEntryShouldBeFound("accountId.equals=" + accountId);

        // Get all the ledgerEntryList where account equals to (accountId + 1)
        defaultLedgerEntryShouldNotBeFound("accountId.equals=" + (accountId + 1));
    }

    @Test
    @Transactional
    void getAllLedgerEntriesByTransactionIsEqualToSomething() throws Exception {
        TransactionRecord transaction;
        if (TestUtil.findAll(em, TransactionRecord.class).isEmpty()) {
            ledgerEntryRepository.saveAndFlush(ledgerEntry);
            transaction = TransactionRecordResourceIT.createEntity();
        } else {
            transaction = TestUtil.findAll(em, TransactionRecord.class).get(0);
        }
        em.persist(transaction);
        em.flush();
        ledgerEntry.setTransaction(transaction);
        ledgerEntryRepository.saveAndFlush(ledgerEntry);
        Long transactionId = transaction.getId();
        // Get all the ledgerEntryList where transaction equals to transactionId
        defaultLedgerEntryShouldBeFound("transactionId.equals=" + transactionId);

        // Get all the ledgerEntryList where transaction equals to (transactionId + 1)
        defaultLedgerEntryShouldNotBeFound("transactionId.equals=" + (transactionId + 1));
    }

    private void defaultLedgerEntryFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultLedgerEntryShouldBeFound(shouldBeFound);
        defaultLedgerEntryShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultLedgerEntryShouldBeFound(String filter) throws Exception {
        restLedgerEntryMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(ledgerEntry.getId().intValue())))
            .andExpect(jsonPath("$.[*].amount").value(hasItem(sameNumber(DEFAULT_AMOUNT))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY.toString())))
            .andExpect(jsonPath("$.[*].entryType").value(hasItem(DEFAULT_ENTRY_TYPE.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));

        // Check, that the count call also returns 1
        restLedgerEntryMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultLedgerEntryShouldNotBeFound(String filter) throws Exception {
        restLedgerEntryMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restLedgerEntryMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingLedgerEntry() throws Exception {
        // Get the ledgerEntry
        restLedgerEntryMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingLedgerEntry() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the ledgerEntry
        LedgerEntry updatedLedgerEntry = ledgerEntryRepository.findById(ledgerEntry.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedLedgerEntry are not directly saved in db
        em.detach(updatedLedgerEntry);
        updatedLedgerEntry.amount(UPDATED_AMOUNT).currency(UPDATED_CURRENCY).entryType(UPDATED_ENTRY_TYPE).createdAt(UPDATED_CREATED_AT);
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(updatedLedgerEntry);

        restLedgerEntryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, ledgerEntryDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(ledgerEntryDTO))
            )
            .andExpect(status().isOk());

        // Validate the LedgerEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedLedgerEntryToMatchAllProperties(updatedLedgerEntry);
    }

    @Test
    @Transactional
    void putNonExistingLedgerEntry() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ledgerEntry.setId(longCount.incrementAndGet());

        // Create the LedgerEntry
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(ledgerEntry);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restLedgerEntryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, ledgerEntryDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(ledgerEntryDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the LedgerEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchLedgerEntry() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ledgerEntry.setId(longCount.incrementAndGet());

        // Create the LedgerEntry
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(ledgerEntry);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restLedgerEntryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(ledgerEntryDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the LedgerEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamLedgerEntry() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ledgerEntry.setId(longCount.incrementAndGet());

        // Create the LedgerEntry
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(ledgerEntry);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restLedgerEntryMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerEntryDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the LedgerEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateLedgerEntryWithPatch() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the ledgerEntry using partial update
        LedgerEntry partialUpdatedLedgerEntry = new LedgerEntry();
        partialUpdatedLedgerEntry.setId(ledgerEntry.getId());

        partialUpdatedLedgerEntry.amount(UPDATED_AMOUNT).currency(UPDATED_CURRENCY).entryType(UPDATED_ENTRY_TYPE);

        restLedgerEntryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedLedgerEntry.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedLedgerEntry))
            )
            .andExpect(status().isOk());

        // Validate the LedgerEntry in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertLedgerEntryUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedLedgerEntry, ledgerEntry),
            getPersistedLedgerEntry(ledgerEntry)
        );
    }

    @Test
    @Transactional
    void fullUpdateLedgerEntryWithPatch() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the ledgerEntry using partial update
        LedgerEntry partialUpdatedLedgerEntry = new LedgerEntry();
        partialUpdatedLedgerEntry.setId(ledgerEntry.getId());

        partialUpdatedLedgerEntry
            .amount(UPDATED_AMOUNT)
            .currency(UPDATED_CURRENCY)
            .entryType(UPDATED_ENTRY_TYPE)
            .createdAt(UPDATED_CREATED_AT);

        restLedgerEntryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedLedgerEntry.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedLedgerEntry))
            )
            .andExpect(status().isOk());

        // Validate the LedgerEntry in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertLedgerEntryUpdatableFieldsEquals(partialUpdatedLedgerEntry, getPersistedLedgerEntry(partialUpdatedLedgerEntry));
    }

    @Test
    @Transactional
    void patchNonExistingLedgerEntry() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ledgerEntry.setId(longCount.incrementAndGet());

        // Create the LedgerEntry
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(ledgerEntry);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restLedgerEntryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, ledgerEntryDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(ledgerEntryDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the LedgerEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchLedgerEntry() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ledgerEntry.setId(longCount.incrementAndGet());

        // Create the LedgerEntry
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(ledgerEntry);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restLedgerEntryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(ledgerEntryDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the LedgerEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamLedgerEntry() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ledgerEntry.setId(longCount.incrementAndGet());

        // Create the LedgerEntry
        LedgerEntryDTO ledgerEntryDTO = ledgerEntryMapper.toDto(ledgerEntry);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restLedgerEntryMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(ledgerEntryDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the LedgerEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteLedgerEntry() throws Exception {
        // Initialize the database
        insertedLedgerEntry = ledgerEntryRepository.saveAndFlush(ledgerEntry);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the ledgerEntry
        restLedgerEntryMockMvc
            .perform(delete(ENTITY_API_URL_ID, ledgerEntry.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return ledgerEntryRepository.count();
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

    protected LedgerEntry getPersistedLedgerEntry(LedgerEntry ledgerEntry) {
        return ledgerEntryRepository.findById(ledgerEntry.getId()).orElseThrow();
    }

    protected void assertPersistedLedgerEntryToMatchAllProperties(LedgerEntry expectedLedgerEntry) {
        assertLedgerEntryAllPropertiesEquals(expectedLedgerEntry, getPersistedLedgerEntry(expectedLedgerEntry));
    }

    protected void assertPersistedLedgerEntryToMatchUpdatableProperties(LedgerEntry expectedLedgerEntry) {
        assertLedgerEntryAllUpdatablePropertiesEquals(expectedLedgerEntry, getPersistedLedgerEntry(expectedLedgerEntry));
    }
}
