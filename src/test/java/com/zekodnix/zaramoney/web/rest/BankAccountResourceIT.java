package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.domain.BankAccountAsserts.*;
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
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.enumeration.AccountStatus;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.service.BankAccountService;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.mapper.BankAccountMapper;
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
 * Integration tests for the {@link BankAccountResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class BankAccountResourceIT {

    private static final String DEFAULT_ACCOUNT_NUMBER = "AAAAAAAAAA";
    private static final String UPDATED_ACCOUNT_NUMBER = "BBBBBBBBBB";

    private static final BigDecimal DEFAULT_BALANCE = new BigDecimal(1);
    private static final BigDecimal UPDATED_BALANCE = new BigDecimal(2);
    private static final BigDecimal SMALLER_BALANCE = new BigDecimal(1 - 1);

    private static final Currency DEFAULT_CURRENCY = Currency.USD;
    private static final Currency UPDATED_CURRENCY = Currency.TND;

    private static final AccountStatus DEFAULT_STATUS = AccountStatus.ACTIVE;
    private static final AccountStatus UPDATED_STATUS = AccountStatus.SUSPENDED;

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/bank-accounts";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Mock
    private BankAccountRepository bankAccountRepositoryMock;

    @Autowired
    private BankAccountMapper bankAccountMapper;

    @Mock
    private BankAccountService bankAccountServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restBankAccountMockMvc;

    private BankAccount bankAccount;

    private BankAccount insertedBankAccount;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static BankAccount createEntity() {
        return new BankAccount()
            .accountNumber(DEFAULT_ACCOUNT_NUMBER)
            .balance(DEFAULT_BALANCE)
            .currency(DEFAULT_CURRENCY)
            .status(DEFAULT_STATUS)
            .createdAt(DEFAULT_CREATED_AT);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static BankAccount createUpdatedEntity() {
        return new BankAccount()
            .accountNumber(UPDATED_ACCOUNT_NUMBER)
            .balance(UPDATED_BALANCE)
            .currency(UPDATED_CURRENCY)
            .status(UPDATED_STATUS)
            .createdAt(UPDATED_CREATED_AT);
    }

    @BeforeEach
    void initTest() {
        bankAccount = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedBankAccount != null) {
            bankAccountRepository.delete(insertedBankAccount);
            insertedBankAccount = null;
        }
    }

    @Test
    @Transactional
    void createBankAccount() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the BankAccount
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);
        var returnedBankAccountDTO = om.readValue(
            restBankAccountMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bankAccountDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            BankAccountDTO.class
        );

        // Validate the BankAccount in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedBankAccount = bankAccountMapper.toEntity(returnedBankAccountDTO);
        assertBankAccountUpdatableFieldsEquals(returnedBankAccount, getPersistedBankAccount(returnedBankAccount));

        insertedBankAccount = returnedBankAccount;
    }

    @Test
    @Transactional
    void createBankAccountWithExistingId() throws Exception {
        // Create the BankAccount with an existing ID
        bankAccount.setId(1L);
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restBankAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bankAccountDTO)))
            .andExpect(status().isBadRequest());

        // Validate the BankAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkAccountNumberIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        bankAccount.setAccountNumber(null);

        // Create the BankAccount, which fails.
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);

        restBankAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bankAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkBalanceIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        bankAccount.setBalance(null);

        // Create the BankAccount, which fails.
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);

        restBankAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bankAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        bankAccount.setCurrency(null);

        // Create the BankAccount, which fails.
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);

        restBankAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bankAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        bankAccount.setStatus(null);

        // Create the BankAccount, which fails.
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);

        restBankAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bankAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        bankAccount.setCreatedAt(null);

        // Create the BankAccount, which fails.
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);

        restBankAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bankAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllBankAccounts() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList
        restBankAccountMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(bankAccount.getId().intValue())))
            .andExpect(jsonPath("$.[*].accountNumber").value(hasItem(DEFAULT_ACCOUNT_NUMBER)))
            .andExpect(jsonPath("$.[*].balance").value(hasItem(sameNumber(DEFAULT_BALANCE))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY.toString())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllBankAccountsWithEagerRelationshipsIsEnabled() throws Exception {
        when(bankAccountServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restBankAccountMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(bankAccountServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllBankAccountsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(bankAccountServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restBankAccountMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(bankAccountRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getBankAccount() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get the bankAccount
        restBankAccountMockMvc
            .perform(get(ENTITY_API_URL_ID, bankAccount.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(bankAccount.getId().intValue()))
            .andExpect(jsonPath("$.accountNumber").value(DEFAULT_ACCOUNT_NUMBER))
            .andExpect(jsonPath("$.balance").value(sameNumber(DEFAULT_BALANCE)))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY.toString()))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()));
    }

    @Test
    @Transactional
    void getBankAccountsByIdFiltering() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        Long id = bankAccount.getId();

        defaultBankAccountFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultBankAccountFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultBankAccountFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllBankAccountsByAccountNumberIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where accountNumber equals to
        defaultBankAccountFiltering("accountNumber.equals=" + DEFAULT_ACCOUNT_NUMBER, "accountNumber.equals=" + UPDATED_ACCOUNT_NUMBER);
    }

    @Test
    @Transactional
    void getAllBankAccountsByAccountNumberIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where accountNumber in
        defaultBankAccountFiltering(
            "accountNumber.in=" + DEFAULT_ACCOUNT_NUMBER + "," + UPDATED_ACCOUNT_NUMBER,
            "accountNumber.in=" + UPDATED_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllBankAccountsByAccountNumberIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where accountNumber is not null
        defaultBankAccountFiltering("accountNumber.specified=true", "accountNumber.specified=false");
    }

    @Test
    @Transactional
    void getAllBankAccountsByAccountNumberContainsSomething() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where accountNumber contains
        defaultBankAccountFiltering("accountNumber.contains=" + DEFAULT_ACCOUNT_NUMBER, "accountNumber.contains=" + UPDATED_ACCOUNT_NUMBER);
    }

    @Test
    @Transactional
    void getAllBankAccountsByAccountNumberNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where accountNumber does not contain
        defaultBankAccountFiltering(
            "accountNumber.doesNotContain=" + UPDATED_ACCOUNT_NUMBER,
            "accountNumber.doesNotContain=" + DEFAULT_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllBankAccountsByBalanceIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where balance equals to
        defaultBankAccountFiltering("balance.equals=" + DEFAULT_BALANCE, "balance.equals=" + UPDATED_BALANCE);
    }

    @Test
    @Transactional
    void getAllBankAccountsByBalanceIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where balance in
        defaultBankAccountFiltering("balance.in=" + DEFAULT_BALANCE + "," + UPDATED_BALANCE, "balance.in=" + UPDATED_BALANCE);
    }

    @Test
    @Transactional
    void getAllBankAccountsByBalanceIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where balance is not null
        defaultBankAccountFiltering("balance.specified=true", "balance.specified=false");
    }

    @Test
    @Transactional
    void getAllBankAccountsByBalanceIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where balance is greater than or equal to
        defaultBankAccountFiltering("balance.greaterThanOrEqual=" + DEFAULT_BALANCE, "balance.greaterThanOrEqual=" + UPDATED_BALANCE);
    }

    @Test
    @Transactional
    void getAllBankAccountsByBalanceIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where balance is less than or equal to
        defaultBankAccountFiltering("balance.lessThanOrEqual=" + DEFAULT_BALANCE, "balance.lessThanOrEqual=" + SMALLER_BALANCE);
    }

    @Test
    @Transactional
    void getAllBankAccountsByBalanceIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where balance is less than
        defaultBankAccountFiltering("balance.lessThan=" + UPDATED_BALANCE, "balance.lessThan=" + DEFAULT_BALANCE);
    }

    @Test
    @Transactional
    void getAllBankAccountsByBalanceIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where balance is greater than
        defaultBankAccountFiltering("balance.greaterThan=" + SMALLER_BALANCE, "balance.greaterThan=" + DEFAULT_BALANCE);
    }

    @Test
    @Transactional
    void getAllBankAccountsByCurrencyIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where currency equals to
        defaultBankAccountFiltering("currency.equals=" + DEFAULT_CURRENCY, "currency.equals=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllBankAccountsByCurrencyIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where currency in
        defaultBankAccountFiltering("currency.in=" + DEFAULT_CURRENCY + "," + UPDATED_CURRENCY, "currency.in=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllBankAccountsByCurrencyIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where currency is not null
        defaultBankAccountFiltering("currency.specified=true", "currency.specified=false");
    }

    @Test
    @Transactional
    void getAllBankAccountsByStatusIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where status equals to
        defaultBankAccountFiltering("status.equals=" + DEFAULT_STATUS, "status.equals=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllBankAccountsByStatusIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where status in
        defaultBankAccountFiltering("status.in=" + DEFAULT_STATUS + "," + UPDATED_STATUS, "status.in=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllBankAccountsByStatusIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where status is not null
        defaultBankAccountFiltering("status.specified=true", "status.specified=false");
    }

    @Test
    @Transactional
    void getAllBankAccountsByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where createdAt equals to
        defaultBankAccountFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllBankAccountsByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where createdAt in
        defaultBankAccountFiltering("createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT, "createdAt.in=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllBankAccountsByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        // Get all the bankAccountList where createdAt is not null
        defaultBankAccountFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllBankAccountsByUserIsEqualToSomething() throws Exception {
        User user;
        if (TestUtil.findAll(em, User.class).isEmpty()) {
            bankAccountRepository.saveAndFlush(bankAccount);
            user = UserResourceIT.createEntity();
        } else {
            user = TestUtil.findAll(em, User.class).get(0);
        }
        em.persist(user);
        em.flush();
        bankAccount.setUser(user);
        bankAccountRepository.saveAndFlush(bankAccount);
        Long userId = user.getId();
        // Get all the bankAccountList where user equals to userId
        defaultBankAccountShouldBeFound("userId.equals=" + userId);

        // Get all the bankAccountList where user equals to (userId + 1)
        defaultBankAccountShouldNotBeFound("userId.equals=" + (userId + 1));
    }

    private void defaultBankAccountFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultBankAccountShouldBeFound(shouldBeFound);
        defaultBankAccountShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultBankAccountShouldBeFound(String filter) throws Exception {
        restBankAccountMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(bankAccount.getId().intValue())))
            .andExpect(jsonPath("$.[*].accountNumber").value(hasItem(DEFAULT_ACCOUNT_NUMBER)))
            .andExpect(jsonPath("$.[*].balance").value(hasItem(sameNumber(DEFAULT_BALANCE))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY.toString())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));

        // Check, that the count call also returns 1
        restBankAccountMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultBankAccountShouldNotBeFound(String filter) throws Exception {
        restBankAccountMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restBankAccountMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingBankAccount() throws Exception {
        // Get the bankAccount
        restBankAccountMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingBankAccount() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the bankAccount
        BankAccount updatedBankAccount = bankAccountRepository.findById(bankAccount.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedBankAccount are not directly saved in db
        em.detach(updatedBankAccount);
        updatedBankAccount
            .accountNumber(UPDATED_ACCOUNT_NUMBER)
            .balance(UPDATED_BALANCE)
            .currency(UPDATED_CURRENCY)
            .status(UPDATED_STATUS)
            .createdAt(UPDATED_CREATED_AT);
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(updatedBankAccount);

        restBankAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, bankAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(bankAccountDTO))
            )
            .andExpect(status().isOk());

        // Validate the BankAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedBankAccountToMatchAllProperties(updatedBankAccount);
    }

    @Test
    @Transactional
    void putNonExistingBankAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        bankAccount.setId(longCount.incrementAndGet());

        // Create the BankAccount
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restBankAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, bankAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(bankAccountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the BankAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchBankAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        bankAccount.setId(longCount.incrementAndGet());

        // Create the BankAccount
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBankAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(bankAccountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the BankAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamBankAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        bankAccount.setId(longCount.incrementAndGet());

        // Create the BankAccount
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBankAccountMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bankAccountDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the BankAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateBankAccountWithPatch() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the bankAccount using partial update
        BankAccount partialUpdatedBankAccount = new BankAccount();
        partialUpdatedBankAccount.setId(bankAccount.getId());

        partialUpdatedBankAccount.balance(UPDATED_BALANCE).currency(UPDATED_CURRENCY).status(UPDATED_STATUS);

        restBankAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedBankAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedBankAccount))
            )
            .andExpect(status().isOk());

        // Validate the BankAccount in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertBankAccountUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedBankAccount, bankAccount),
            getPersistedBankAccount(bankAccount)
        );
    }

    @Test
    @Transactional
    void fullUpdateBankAccountWithPatch() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the bankAccount using partial update
        BankAccount partialUpdatedBankAccount = new BankAccount();
        partialUpdatedBankAccount.setId(bankAccount.getId());

        partialUpdatedBankAccount
            .accountNumber(UPDATED_ACCOUNT_NUMBER)
            .balance(UPDATED_BALANCE)
            .currency(UPDATED_CURRENCY)
            .status(UPDATED_STATUS)
            .createdAt(UPDATED_CREATED_AT);

        restBankAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedBankAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedBankAccount))
            )
            .andExpect(status().isOk());

        // Validate the BankAccount in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertBankAccountUpdatableFieldsEquals(partialUpdatedBankAccount, getPersistedBankAccount(partialUpdatedBankAccount));
    }

    @Test
    @Transactional
    void patchNonExistingBankAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        bankAccount.setId(longCount.incrementAndGet());

        // Create the BankAccount
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restBankAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, bankAccountDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(bankAccountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the BankAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchBankAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        bankAccount.setId(longCount.incrementAndGet());

        // Create the BankAccount
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBankAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(bankAccountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the BankAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamBankAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        bankAccount.setId(longCount.incrementAndGet());

        // Create the BankAccount
        BankAccountDTO bankAccountDTO = bankAccountMapper.toDto(bankAccount);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBankAccountMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(bankAccountDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the BankAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteBankAccount() throws Exception {
        // Initialize the database
        insertedBankAccount = bankAccountRepository.saveAndFlush(bankAccount);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the bankAccount
        restBankAccountMockMvc
            .perform(delete(ENTITY_API_URL_ID, bankAccount.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return bankAccountRepository.count();
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

    protected BankAccount getPersistedBankAccount(BankAccount bankAccount) {
        return bankAccountRepository.findById(bankAccount.getId()).orElseThrow();
    }

    protected void assertPersistedBankAccountToMatchAllProperties(BankAccount expectedBankAccount) {
        assertBankAccountAllPropertiesEquals(expectedBankAccount, getPersistedBankAccount(expectedBankAccount));
    }

    protected void assertPersistedBankAccountToMatchUpdatableProperties(BankAccount expectedBankAccount) {
        assertBankAccountAllUpdatablePropertiesEquals(expectedBankAccount, getPersistedBankAccount(expectedBankAccount));
    }
}
