package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.domain.TransactionRecordAsserts.*;
import static com.zekodnix.zaramoney.web.rest.TestUtil.createUpdateProxyForBean;
import static com.zekodnix.zaramoney.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.domain.TransactionRecord;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.enumeration.FraudStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionStatus;
import com.zekodnix.zaramoney.domain.enumeration.TransactionType;
import com.zekodnix.zaramoney.repository.TransactionRecordRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.service.TransactionRecordService;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import com.zekodnix.zaramoney.service.mapper.TransactionRecordMapper;
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
 * Integration tests for the {@link TransactionRecordResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class TransactionRecordResourceIT {

    private static final TransactionType DEFAULT_TRANSACTION_TYPE = TransactionType.DEPOSIT;
    private static final TransactionType UPDATED_TRANSACTION_TYPE = TransactionType.WITHDRAWAL;

    private static final BigDecimal DEFAULT_SEND_AMOUNT = new BigDecimal(1);
    private static final BigDecimal UPDATED_SEND_AMOUNT = new BigDecimal(2);
    private static final BigDecimal SMALLER_SEND_AMOUNT = new BigDecimal(1 - 1);

    private static final BigDecimal DEFAULT_RECEIVE_AMOUNT = new BigDecimal(1);
    private static final BigDecimal UPDATED_RECEIVE_AMOUNT = new BigDecimal(2);
    private static final BigDecimal SMALLER_RECEIVE_AMOUNT = new BigDecimal(1 - 1);

    private static final Instant DEFAULT_TRANSACTION_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_TRANSACTION_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_SENDER_ACCOUNT_NUMBER = "AAAAAAAAAA";
    private static final String UPDATED_SENDER_ACCOUNT_NUMBER = "BBBBBBBBBB";

    private static final String DEFAULT_RECEIVER_ACCOUNT_NUMBER = "AAAAAAAAAA";
    private static final String UPDATED_RECEIVER_ACCOUNT_NUMBER = "BBBBBBBBBB";

    private static final String DEFAULT_CURRENCY_SEND_AMOUNT = "AAA";
    private static final String UPDATED_CURRENCY_SEND_AMOUNT = "BBB";

    private static final String DEFAULT_CURRENCY_RECEIVE_AMOUNT = "AAA";
    private static final String UPDATED_CURRENCY_RECEIVE_AMOUNT = "BBB";

    private static final TransactionStatus DEFAULT_TRANSACTION_STATUS = TransactionStatus.PENDING;
    private static final TransactionStatus UPDATED_TRANSACTION_STATUS = TransactionStatus.COMPLETED;

    private static final String DEFAULT_TRANSACTION_REFERENCE = "AAAAAAAAAA";
    private static final String UPDATED_TRANSACTION_REFERENCE = "BBBBBBBBBB";

    private static final Integer DEFAULT_RISK_SCORE = 0;
    private static final Integer UPDATED_RISK_SCORE = 1;
    private static final Integer SMALLER_RISK_SCORE = 0 - 1;

    private static final FraudStatus DEFAULT_FRAUD_STATUS = FraudStatus.CLEAN;
    private static final FraudStatus UPDATED_FRAUD_STATUS = FraudStatus.SUSPICIOUS;

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/transaction-records";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Mock
    private TransactionRecordRepository transactionRecordRepositoryMock;

    @Autowired
    private TransactionRecordMapper transactionRecordMapper;

    @Mock
    private TransactionRecordService transactionRecordServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restTransactionRecordMockMvc;

    private TransactionRecord transactionRecord;

    private TransactionRecord insertedTransactionRecord;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static TransactionRecord createEntity() {
        return new TransactionRecord()
            .transactionType(DEFAULT_TRANSACTION_TYPE)
            .sendAmount(DEFAULT_SEND_AMOUNT)
            .receiveAmount(DEFAULT_RECEIVE_AMOUNT)
            .transactionDate(DEFAULT_TRANSACTION_DATE)
            .description(DEFAULT_DESCRIPTION)
            .senderAccountNumber(DEFAULT_SENDER_ACCOUNT_NUMBER)
            .receiverAccountNumber(DEFAULT_RECEIVER_ACCOUNT_NUMBER)
            .currencySendAmount(DEFAULT_CURRENCY_SEND_AMOUNT)
            .currencyReceiveAmount(DEFAULT_CURRENCY_RECEIVE_AMOUNT)
            .transactionStatus(DEFAULT_TRANSACTION_STATUS)
            .transactionReference(DEFAULT_TRANSACTION_REFERENCE)
            .riskScore(DEFAULT_RISK_SCORE)
            .fraudStatus(DEFAULT_FRAUD_STATUS)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static TransactionRecord createUpdatedEntity() {
        return new TransactionRecord()
            .transactionType(UPDATED_TRANSACTION_TYPE)
            .sendAmount(UPDATED_SEND_AMOUNT)
            .receiveAmount(UPDATED_RECEIVE_AMOUNT)
            .transactionDate(UPDATED_TRANSACTION_DATE)
            .description(UPDATED_DESCRIPTION)
            .senderAccountNumber(UPDATED_SENDER_ACCOUNT_NUMBER)
            .receiverAccountNumber(UPDATED_RECEIVER_ACCOUNT_NUMBER)
            .currencySendAmount(UPDATED_CURRENCY_SEND_AMOUNT)
            .currencyReceiveAmount(UPDATED_CURRENCY_RECEIVE_AMOUNT)
            .transactionStatus(UPDATED_TRANSACTION_STATUS)
            .transactionReference(UPDATED_TRANSACTION_REFERENCE)
            .riskScore(UPDATED_RISK_SCORE)
            .fraudStatus(UPDATED_FRAUD_STATUS)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
    }

    @BeforeEach
    void initTest() {
        transactionRecord = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedTransactionRecord != null) {
            transactionRecordRepository.delete(insertedTransactionRecord);
            insertedTransactionRecord = null;
        }
    }

    @Test
    @Transactional
    void createTransactionRecord() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the TransactionRecord
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);
        var returnedTransactionRecordDTO = om.readValue(
            restTransactionRecordMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            TransactionRecordDTO.class
        );

        // Validate the TransactionRecord in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedTransactionRecord = transactionRecordMapper.toEntity(returnedTransactionRecordDTO);
        assertTransactionRecordUpdatableFieldsEquals(returnedTransactionRecord, getPersistedTransactionRecord(returnedTransactionRecord));

        insertedTransactionRecord = returnedTransactionRecord;
    }

    @Test
    @Transactional
    void createTransactionRecordWithExistingId() throws Exception {
        // Create the TransactionRecord with an existing ID
        transactionRecord.setId(1L);
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        // Validate the TransactionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkTransactionTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setTransactionType(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkSendAmountIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setSendAmount(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkReceiveAmountIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setReceiveAmount(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkTransactionDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setTransactionDate(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkSenderAccountNumberIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setSenderAccountNumber(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkReceiverAccountNumberIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setReceiverAccountNumber(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencySendAmountIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setCurrencySendAmount(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencyReceiveAmountIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setCurrencyReceiveAmount(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkTransactionStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setTransactionStatus(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkTransactionReferenceIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setTransactionReference(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkRiskScoreIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setRiskScore(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkFraudStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setFraudStatus(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        transactionRecord.setCreatedAt(null);

        // Create the TransactionRecord, which fails.
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        restTransactionRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllTransactionRecords() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList
        restTransactionRecordMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionRecord.getId().intValue())))
            .andExpect(jsonPath("$.[*].transactionType").value(hasItem(DEFAULT_TRANSACTION_TYPE.toString())))
            .andExpect(jsonPath("$.[*].sendAmount").value(hasItem(sameNumber(DEFAULT_SEND_AMOUNT))))
            .andExpect(jsonPath("$.[*].receiveAmount").value(hasItem(sameNumber(DEFAULT_RECEIVE_AMOUNT))))
            .andExpect(jsonPath("$.[*].transactionDate").value(hasItem(DEFAULT_TRANSACTION_DATE.toString())))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].senderAccountNumber").value(hasItem(DEFAULT_SENDER_ACCOUNT_NUMBER)))
            .andExpect(jsonPath("$.[*].receiverAccountNumber").value(hasItem(DEFAULT_RECEIVER_ACCOUNT_NUMBER)))
            .andExpect(jsonPath("$.[*].currencySendAmount").value(hasItem(DEFAULT_CURRENCY_SEND_AMOUNT)))
            .andExpect(jsonPath("$.[*].currencyReceiveAmount").value(hasItem(DEFAULT_CURRENCY_RECEIVE_AMOUNT)))
            .andExpect(jsonPath("$.[*].transactionStatus").value(hasItem(DEFAULT_TRANSACTION_STATUS.toString())))
            .andExpect(jsonPath("$.[*].transactionReference").value(hasItem(DEFAULT_TRANSACTION_REFERENCE)))
            .andExpect(jsonPath("$.[*].riskScore").value(hasItem(DEFAULT_RISK_SCORE)))
            .andExpect(jsonPath("$.[*].fraudStatus").value(hasItem(DEFAULT_FRAUD_STATUS.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTransactionRecordsWithEagerRelationshipsIsEnabled() throws Exception {
        when(transactionRecordServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTransactionRecordMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(transactionRecordServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllTransactionRecordsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(transactionRecordServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restTransactionRecordMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(transactionRecordRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getTransactionRecord() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get the transactionRecord
        restTransactionRecordMockMvc
            .perform(get(ENTITY_API_URL_ID, transactionRecord.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(transactionRecord.getId().intValue()))
            .andExpect(jsonPath("$.transactionType").value(DEFAULT_TRANSACTION_TYPE.toString()))
            .andExpect(jsonPath("$.sendAmount").value(sameNumber(DEFAULT_SEND_AMOUNT)))
            .andExpect(jsonPath("$.receiveAmount").value(sameNumber(DEFAULT_RECEIVE_AMOUNT)))
            .andExpect(jsonPath("$.transactionDate").value(DEFAULT_TRANSACTION_DATE.toString()))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.senderAccountNumber").value(DEFAULT_SENDER_ACCOUNT_NUMBER))
            .andExpect(jsonPath("$.receiverAccountNumber").value(DEFAULT_RECEIVER_ACCOUNT_NUMBER))
            .andExpect(jsonPath("$.currencySendAmount").value(DEFAULT_CURRENCY_SEND_AMOUNT))
            .andExpect(jsonPath("$.currencyReceiveAmount").value(DEFAULT_CURRENCY_RECEIVE_AMOUNT))
            .andExpect(jsonPath("$.transactionStatus").value(DEFAULT_TRANSACTION_STATUS.toString()))
            .andExpect(jsonPath("$.transactionReference").value(DEFAULT_TRANSACTION_REFERENCE))
            .andExpect(jsonPath("$.riskScore").value(DEFAULT_RISK_SCORE))
            .andExpect(jsonPath("$.fraudStatus").value(DEFAULT_FRAUD_STATUS.toString()))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()));
    }

    @Test
    @Transactional
    void getTransactionRecordsByIdFiltering() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        Long id = transactionRecord.getId();

        defaultTransactionRecordFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultTransactionRecordFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultTransactionRecordFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionTypeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionType equals to
        defaultTransactionRecordFiltering(
            "transactionType.equals=" + DEFAULT_TRANSACTION_TYPE,
            "transactionType.equals=" + UPDATED_TRANSACTION_TYPE
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionTypeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionType in
        defaultTransactionRecordFiltering(
            "transactionType.in=" + DEFAULT_TRANSACTION_TYPE + "," + UPDATED_TRANSACTION_TYPE,
            "transactionType.in=" + UPDATED_TRANSACTION_TYPE
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionTypeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionType is not null
        defaultTransactionRecordFiltering("transactionType.specified=true", "transactionType.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsBySendAmountIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where sendAmount equals to
        defaultTransactionRecordFiltering("sendAmount.equals=" + DEFAULT_SEND_AMOUNT, "sendAmount.equals=" + UPDATED_SEND_AMOUNT);
    }

    @Test
    @Transactional
    void getAllTransactionRecordsBySendAmountIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where sendAmount in
        defaultTransactionRecordFiltering(
            "sendAmount.in=" + DEFAULT_SEND_AMOUNT + "," + UPDATED_SEND_AMOUNT,
            "sendAmount.in=" + UPDATED_SEND_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsBySendAmountIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where sendAmount is not null
        defaultTransactionRecordFiltering("sendAmount.specified=true", "sendAmount.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsBySendAmountIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where sendAmount is greater than or equal to
        defaultTransactionRecordFiltering(
            "sendAmount.greaterThanOrEqual=" + DEFAULT_SEND_AMOUNT,
            "sendAmount.greaterThanOrEqual=" + UPDATED_SEND_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsBySendAmountIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where sendAmount is less than or equal to
        defaultTransactionRecordFiltering(
            "sendAmount.lessThanOrEqual=" + DEFAULT_SEND_AMOUNT,
            "sendAmount.lessThanOrEqual=" + SMALLER_SEND_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsBySendAmountIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where sendAmount is less than
        defaultTransactionRecordFiltering("sendAmount.lessThan=" + UPDATED_SEND_AMOUNT, "sendAmount.lessThan=" + DEFAULT_SEND_AMOUNT);
    }

    @Test
    @Transactional
    void getAllTransactionRecordsBySendAmountIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where sendAmount is greater than
        defaultTransactionRecordFiltering("sendAmount.greaterThan=" + SMALLER_SEND_AMOUNT, "sendAmount.greaterThan=" + DEFAULT_SEND_AMOUNT);
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByReceiveAmountIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where receiveAmount equals to
        defaultTransactionRecordFiltering(
            "receiveAmount.equals=" + DEFAULT_RECEIVE_AMOUNT,
            "receiveAmount.equals=" + UPDATED_RECEIVE_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByReceiveAmountIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where receiveAmount in
        defaultTransactionRecordFiltering(
            "receiveAmount.in=" + DEFAULT_RECEIVE_AMOUNT + "," + UPDATED_RECEIVE_AMOUNT,
            "receiveAmount.in=" + UPDATED_RECEIVE_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByReceiveAmountIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where receiveAmount is not null
        defaultTransactionRecordFiltering("receiveAmount.specified=true", "receiveAmount.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByReceiveAmountIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where receiveAmount is greater than or equal to
        defaultTransactionRecordFiltering(
            "receiveAmount.greaterThanOrEqual=" + DEFAULT_RECEIVE_AMOUNT,
            "receiveAmount.greaterThanOrEqual=" + UPDATED_RECEIVE_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByReceiveAmountIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where receiveAmount is less than or equal to
        defaultTransactionRecordFiltering(
            "receiveAmount.lessThanOrEqual=" + DEFAULT_RECEIVE_AMOUNT,
            "receiveAmount.lessThanOrEqual=" + SMALLER_RECEIVE_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByReceiveAmountIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where receiveAmount is less than
        defaultTransactionRecordFiltering(
            "receiveAmount.lessThan=" + UPDATED_RECEIVE_AMOUNT,
            "receiveAmount.lessThan=" + DEFAULT_RECEIVE_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByReceiveAmountIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where receiveAmount is greater than
        defaultTransactionRecordFiltering(
            "receiveAmount.greaterThan=" + SMALLER_RECEIVE_AMOUNT,
            "receiveAmount.greaterThan=" + DEFAULT_RECEIVE_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionDate equals to
        defaultTransactionRecordFiltering(
            "transactionDate.equals=" + DEFAULT_TRANSACTION_DATE,
            "transactionDate.equals=" + UPDATED_TRANSACTION_DATE
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionDate in
        defaultTransactionRecordFiltering(
            "transactionDate.in=" + DEFAULT_TRANSACTION_DATE + "," + UPDATED_TRANSACTION_DATE,
            "transactionDate.in=" + UPDATED_TRANSACTION_DATE
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionDate is not null
        defaultTransactionRecordFiltering("transactionDate.specified=true", "transactionDate.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByDescriptionIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where description equals to
        defaultTransactionRecordFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByDescriptionIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where description in
        defaultTransactionRecordFiltering(
            "description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION,
            "description.in=" + UPDATED_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByDescriptionIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where description is not null
        defaultTransactionRecordFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByDescriptionContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where description contains
        defaultTransactionRecordFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByDescriptionNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where description does not contain
        defaultTransactionRecordFiltering(
            "description.doesNotContain=" + UPDATED_DESCRIPTION,
            "description.doesNotContain=" + DEFAULT_DESCRIPTION
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsBySenderAccountNumberIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where senderAccountNumber equals to
        defaultTransactionRecordFiltering(
            "senderAccountNumber.equals=" + DEFAULT_SENDER_ACCOUNT_NUMBER,
            "senderAccountNumber.equals=" + UPDATED_SENDER_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsBySenderAccountNumberIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where senderAccountNumber in
        defaultTransactionRecordFiltering(
            "senderAccountNumber.in=" + DEFAULT_SENDER_ACCOUNT_NUMBER + "," + UPDATED_SENDER_ACCOUNT_NUMBER,
            "senderAccountNumber.in=" + UPDATED_SENDER_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsBySenderAccountNumberIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where senderAccountNumber is not null
        defaultTransactionRecordFiltering("senderAccountNumber.specified=true", "senderAccountNumber.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsBySenderAccountNumberContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where senderAccountNumber contains
        defaultTransactionRecordFiltering(
            "senderAccountNumber.contains=" + DEFAULT_SENDER_ACCOUNT_NUMBER,
            "senderAccountNumber.contains=" + UPDATED_SENDER_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsBySenderAccountNumberNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where senderAccountNumber does not contain
        defaultTransactionRecordFiltering(
            "senderAccountNumber.doesNotContain=" + UPDATED_SENDER_ACCOUNT_NUMBER,
            "senderAccountNumber.doesNotContain=" + DEFAULT_SENDER_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByReceiverAccountNumberIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where receiverAccountNumber equals to
        defaultTransactionRecordFiltering(
            "receiverAccountNumber.equals=" + DEFAULT_RECEIVER_ACCOUNT_NUMBER,
            "receiverAccountNumber.equals=" + UPDATED_RECEIVER_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByReceiverAccountNumberIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where receiverAccountNumber in
        defaultTransactionRecordFiltering(
            "receiverAccountNumber.in=" + DEFAULT_RECEIVER_ACCOUNT_NUMBER + "," + UPDATED_RECEIVER_ACCOUNT_NUMBER,
            "receiverAccountNumber.in=" + UPDATED_RECEIVER_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByReceiverAccountNumberIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where receiverAccountNumber is not null
        defaultTransactionRecordFiltering("receiverAccountNumber.specified=true", "receiverAccountNumber.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByReceiverAccountNumberContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where receiverAccountNumber contains
        defaultTransactionRecordFiltering(
            "receiverAccountNumber.contains=" + DEFAULT_RECEIVER_ACCOUNT_NUMBER,
            "receiverAccountNumber.contains=" + UPDATED_RECEIVER_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByReceiverAccountNumberNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where receiverAccountNumber does not contain
        defaultTransactionRecordFiltering(
            "receiverAccountNumber.doesNotContain=" + UPDATED_RECEIVER_ACCOUNT_NUMBER,
            "receiverAccountNumber.doesNotContain=" + DEFAULT_RECEIVER_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCurrencySendAmountIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where currencySendAmount equals to
        defaultTransactionRecordFiltering(
            "currencySendAmount.equals=" + DEFAULT_CURRENCY_SEND_AMOUNT,
            "currencySendAmount.equals=" + UPDATED_CURRENCY_SEND_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCurrencySendAmountIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where currencySendAmount in
        defaultTransactionRecordFiltering(
            "currencySendAmount.in=" + DEFAULT_CURRENCY_SEND_AMOUNT + "," + UPDATED_CURRENCY_SEND_AMOUNT,
            "currencySendAmount.in=" + UPDATED_CURRENCY_SEND_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCurrencySendAmountIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where currencySendAmount is not null
        defaultTransactionRecordFiltering("currencySendAmount.specified=true", "currencySendAmount.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCurrencySendAmountContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where currencySendAmount contains
        defaultTransactionRecordFiltering(
            "currencySendAmount.contains=" + DEFAULT_CURRENCY_SEND_AMOUNT,
            "currencySendAmount.contains=" + UPDATED_CURRENCY_SEND_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCurrencySendAmountNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where currencySendAmount does not contain
        defaultTransactionRecordFiltering(
            "currencySendAmount.doesNotContain=" + UPDATED_CURRENCY_SEND_AMOUNT,
            "currencySendAmount.doesNotContain=" + DEFAULT_CURRENCY_SEND_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCurrencyReceiveAmountIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where currencyReceiveAmount equals to
        defaultTransactionRecordFiltering(
            "currencyReceiveAmount.equals=" + DEFAULT_CURRENCY_RECEIVE_AMOUNT,
            "currencyReceiveAmount.equals=" + UPDATED_CURRENCY_RECEIVE_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCurrencyReceiveAmountIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where currencyReceiveAmount in
        defaultTransactionRecordFiltering(
            "currencyReceiveAmount.in=" + DEFAULT_CURRENCY_RECEIVE_AMOUNT + "," + UPDATED_CURRENCY_RECEIVE_AMOUNT,
            "currencyReceiveAmount.in=" + UPDATED_CURRENCY_RECEIVE_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCurrencyReceiveAmountIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where currencyReceiveAmount is not null
        defaultTransactionRecordFiltering("currencyReceiveAmount.specified=true", "currencyReceiveAmount.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCurrencyReceiveAmountContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where currencyReceiveAmount contains
        defaultTransactionRecordFiltering(
            "currencyReceiveAmount.contains=" + DEFAULT_CURRENCY_RECEIVE_AMOUNT,
            "currencyReceiveAmount.contains=" + UPDATED_CURRENCY_RECEIVE_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCurrencyReceiveAmountNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where currencyReceiveAmount does not contain
        defaultTransactionRecordFiltering(
            "currencyReceiveAmount.doesNotContain=" + UPDATED_CURRENCY_RECEIVE_AMOUNT,
            "currencyReceiveAmount.doesNotContain=" + DEFAULT_CURRENCY_RECEIVE_AMOUNT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionStatusIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionStatus equals to
        defaultTransactionRecordFiltering(
            "transactionStatus.equals=" + DEFAULT_TRANSACTION_STATUS,
            "transactionStatus.equals=" + UPDATED_TRANSACTION_STATUS
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionStatusIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionStatus in
        defaultTransactionRecordFiltering(
            "transactionStatus.in=" + DEFAULT_TRANSACTION_STATUS + "," + UPDATED_TRANSACTION_STATUS,
            "transactionStatus.in=" + UPDATED_TRANSACTION_STATUS
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionStatusIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionStatus is not null
        defaultTransactionRecordFiltering("transactionStatus.specified=true", "transactionStatus.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionReferenceIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionReference equals to
        defaultTransactionRecordFiltering(
            "transactionReference.equals=" + DEFAULT_TRANSACTION_REFERENCE,
            "transactionReference.equals=" + UPDATED_TRANSACTION_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionReferenceIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionReference in
        defaultTransactionRecordFiltering(
            "transactionReference.in=" + DEFAULT_TRANSACTION_REFERENCE + "," + UPDATED_TRANSACTION_REFERENCE,
            "transactionReference.in=" + UPDATED_TRANSACTION_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionReferenceIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionReference is not null
        defaultTransactionRecordFiltering("transactionReference.specified=true", "transactionReference.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionReferenceContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionReference contains
        defaultTransactionRecordFiltering(
            "transactionReference.contains=" + DEFAULT_TRANSACTION_REFERENCE,
            "transactionReference.contains=" + UPDATED_TRANSACTION_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByTransactionReferenceNotContainsSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where transactionReference does not contain
        defaultTransactionRecordFiltering(
            "transactionReference.doesNotContain=" + UPDATED_TRANSACTION_REFERENCE,
            "transactionReference.doesNotContain=" + DEFAULT_TRANSACTION_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByRiskScoreIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where riskScore equals to
        defaultTransactionRecordFiltering("riskScore.equals=" + DEFAULT_RISK_SCORE, "riskScore.equals=" + UPDATED_RISK_SCORE);
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByRiskScoreIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where riskScore in
        defaultTransactionRecordFiltering(
            "riskScore.in=" + DEFAULT_RISK_SCORE + "," + UPDATED_RISK_SCORE,
            "riskScore.in=" + UPDATED_RISK_SCORE
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByRiskScoreIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where riskScore is not null
        defaultTransactionRecordFiltering("riskScore.specified=true", "riskScore.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByRiskScoreIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where riskScore is greater than or equal to
        defaultTransactionRecordFiltering(
            "riskScore.greaterThanOrEqual=" + DEFAULT_RISK_SCORE,
            "riskScore.greaterThanOrEqual=" + (DEFAULT_RISK_SCORE + 1)
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByRiskScoreIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where riskScore is less than or equal to
        defaultTransactionRecordFiltering(
            "riskScore.lessThanOrEqual=" + DEFAULT_RISK_SCORE,
            "riskScore.lessThanOrEqual=" + SMALLER_RISK_SCORE
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByRiskScoreIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where riskScore is less than
        defaultTransactionRecordFiltering("riskScore.lessThan=" + (DEFAULT_RISK_SCORE + 1), "riskScore.lessThan=" + DEFAULT_RISK_SCORE);
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByRiskScoreIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where riskScore is greater than
        defaultTransactionRecordFiltering("riskScore.greaterThan=" + SMALLER_RISK_SCORE, "riskScore.greaterThan=" + DEFAULT_RISK_SCORE);
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByFraudStatusIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where fraudStatus equals to
        defaultTransactionRecordFiltering("fraudStatus.equals=" + DEFAULT_FRAUD_STATUS, "fraudStatus.equals=" + UPDATED_FRAUD_STATUS);
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByFraudStatusIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where fraudStatus in
        defaultTransactionRecordFiltering(
            "fraudStatus.in=" + DEFAULT_FRAUD_STATUS + "," + UPDATED_FRAUD_STATUS,
            "fraudStatus.in=" + UPDATED_FRAUD_STATUS
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByFraudStatusIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where fraudStatus is not null
        defaultTransactionRecordFiltering("fraudStatus.specified=true", "fraudStatus.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where createdAt equals to
        defaultTransactionRecordFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where createdAt in
        defaultTransactionRecordFiltering(
            "createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT,
            "createdAt.in=" + UPDATED_CREATED_AT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where createdAt is not null
        defaultTransactionRecordFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByUpdatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where updatedAt equals to
        defaultTransactionRecordFiltering("updatedAt.equals=" + DEFAULT_UPDATED_AT, "updatedAt.equals=" + UPDATED_UPDATED_AT);
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByUpdatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where updatedAt in
        defaultTransactionRecordFiltering(
            "updatedAt.in=" + DEFAULT_UPDATED_AT + "," + UPDATED_UPDATED_AT,
            "updatedAt.in=" + UPDATED_UPDATED_AT
        );
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByUpdatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        // Get all the transactionRecordList where updatedAt is not null
        defaultTransactionRecordFiltering("updatedAt.specified=true", "updatedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllTransactionRecordsByUserLoginIsEqualToSomething() throws Exception {
        User userLogin;
        if (TestUtil.findAll(em, User.class).isEmpty()) {
            transactionRecordRepository.saveAndFlush(transactionRecord);
            userLogin = UserResourceIT.createEntity();
        } else {
            userLogin = TestUtil.findAll(em, User.class).get(0);
        }
        em.persist(userLogin);
        em.flush();
        transactionRecord.setUserLogin(userLogin);
        transactionRecordRepository.saveAndFlush(transactionRecord);
        Long userLoginId = userLogin.getId();
        // Get all the transactionRecordList where userLogin equals to userLoginId
        defaultTransactionRecordShouldBeFound("userLoginId.equals=" + userLoginId);

        // Get all the transactionRecordList where userLogin equals to (userLoginId + 1)
        defaultTransactionRecordShouldNotBeFound("userLoginId.equals=" + (userLoginId + 1));
    }

    private void defaultTransactionRecordFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultTransactionRecordShouldBeFound(shouldBeFound);
        defaultTransactionRecordShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultTransactionRecordShouldBeFound(String filter) throws Exception {
        restTransactionRecordMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(transactionRecord.getId().intValue())))
            .andExpect(jsonPath("$.[*].transactionType").value(hasItem(DEFAULT_TRANSACTION_TYPE.toString())))
            .andExpect(jsonPath("$.[*].sendAmount").value(hasItem(sameNumber(DEFAULT_SEND_AMOUNT))))
            .andExpect(jsonPath("$.[*].receiveAmount").value(hasItem(sameNumber(DEFAULT_RECEIVE_AMOUNT))))
            .andExpect(jsonPath("$.[*].transactionDate").value(hasItem(DEFAULT_TRANSACTION_DATE.toString())))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].senderAccountNumber").value(hasItem(DEFAULT_SENDER_ACCOUNT_NUMBER)))
            .andExpect(jsonPath("$.[*].receiverAccountNumber").value(hasItem(DEFAULT_RECEIVER_ACCOUNT_NUMBER)))
            .andExpect(jsonPath("$.[*].currencySendAmount").value(hasItem(DEFAULT_CURRENCY_SEND_AMOUNT)))
            .andExpect(jsonPath("$.[*].currencyReceiveAmount").value(hasItem(DEFAULT_CURRENCY_RECEIVE_AMOUNT)))
            .andExpect(jsonPath("$.[*].transactionStatus").value(hasItem(DEFAULT_TRANSACTION_STATUS.toString())))
            .andExpect(jsonPath("$.[*].transactionReference").value(hasItem(DEFAULT_TRANSACTION_REFERENCE)))
            .andExpect(jsonPath("$.[*].riskScore").value(hasItem(DEFAULT_RISK_SCORE)))
            .andExpect(jsonPath("$.[*].fraudStatus").value(hasItem(DEFAULT_FRAUD_STATUS.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));

        // Check, that the count call also returns 1
        restTransactionRecordMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultTransactionRecordShouldNotBeFound(String filter) throws Exception {
        restTransactionRecordMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restTransactionRecordMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingTransactionRecord() throws Exception {
        // Get the transactionRecord
        restTransactionRecordMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingTransactionRecord() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionRecord
        TransactionRecord updatedTransactionRecord = transactionRecordRepository.findById(transactionRecord.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedTransactionRecord are not directly saved in db
        em.detach(updatedTransactionRecord);
        updatedTransactionRecord
            .transactionType(UPDATED_TRANSACTION_TYPE)
            .sendAmount(UPDATED_SEND_AMOUNT)
            .receiveAmount(UPDATED_RECEIVE_AMOUNT)
            .transactionDate(UPDATED_TRANSACTION_DATE)
            .description(UPDATED_DESCRIPTION)
            .senderAccountNumber(UPDATED_SENDER_ACCOUNT_NUMBER)
            .receiverAccountNumber(UPDATED_RECEIVER_ACCOUNT_NUMBER)
            .currencySendAmount(UPDATED_CURRENCY_SEND_AMOUNT)
            .currencyReceiveAmount(UPDATED_CURRENCY_RECEIVE_AMOUNT)
            .transactionStatus(UPDATED_TRANSACTION_STATUS)
            .transactionReference(UPDATED_TRANSACTION_REFERENCE)
            .riskScore(UPDATED_RISK_SCORE)
            .fraudStatus(UPDATED_FRAUD_STATUS)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(updatedTransactionRecord);

        restTransactionRecordMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRecordDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRecordDTO))
            )
            .andExpect(status().isOk());

        // Validate the TransactionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedTransactionRecordToMatchAllProperties(updatedTransactionRecord);
    }

    @Test
    @Transactional
    void putNonExistingTransactionRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRecord.setId(longCount.incrementAndGet());

        // Create the TransactionRecord
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTransactionRecordMockMvc
            .perform(
                put(ENTITY_API_URL_ID, transactionRecordDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRecordDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchTransactionRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRecord.setId(longCount.incrementAndGet());

        // Create the TransactionRecord
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionRecordMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(transactionRecordDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamTransactionRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRecord.setId(longCount.incrementAndGet());

        // Create the TransactionRecord
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionRecordMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the TransactionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateTransactionRecordWithPatch() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionRecord using partial update
        TransactionRecord partialUpdatedTransactionRecord = new TransactionRecord();
        partialUpdatedTransactionRecord.setId(transactionRecord.getId());

        partialUpdatedTransactionRecord
            .transactionDate(UPDATED_TRANSACTION_DATE)
            .senderAccountNumber(UPDATED_SENDER_ACCOUNT_NUMBER)
            .receiverAccountNumber(UPDATED_RECEIVER_ACCOUNT_NUMBER)
            .currencySendAmount(UPDATED_CURRENCY_SEND_AMOUNT)
            .currencyReceiveAmount(UPDATED_CURRENCY_RECEIVE_AMOUNT)
            .transactionStatus(UPDATED_TRANSACTION_STATUS)
            .transactionReference(UPDATED_TRANSACTION_REFERENCE)
            .riskScore(UPDATED_RISK_SCORE)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);

        restTransactionRecordMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTransactionRecord.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTransactionRecord))
            )
            .andExpect(status().isOk());

        // Validate the TransactionRecord in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTransactionRecordUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedTransactionRecord, transactionRecord),
            getPersistedTransactionRecord(transactionRecord)
        );
    }

    @Test
    @Transactional
    void fullUpdateTransactionRecordWithPatch() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the transactionRecord using partial update
        TransactionRecord partialUpdatedTransactionRecord = new TransactionRecord();
        partialUpdatedTransactionRecord.setId(transactionRecord.getId());

        partialUpdatedTransactionRecord
            .transactionType(UPDATED_TRANSACTION_TYPE)
            .sendAmount(UPDATED_SEND_AMOUNT)
            .receiveAmount(UPDATED_RECEIVE_AMOUNT)
            .transactionDate(UPDATED_TRANSACTION_DATE)
            .description(UPDATED_DESCRIPTION)
            .senderAccountNumber(UPDATED_SENDER_ACCOUNT_NUMBER)
            .receiverAccountNumber(UPDATED_RECEIVER_ACCOUNT_NUMBER)
            .currencySendAmount(UPDATED_CURRENCY_SEND_AMOUNT)
            .currencyReceiveAmount(UPDATED_CURRENCY_RECEIVE_AMOUNT)
            .transactionStatus(UPDATED_TRANSACTION_STATUS)
            .transactionReference(UPDATED_TRANSACTION_REFERENCE)
            .riskScore(UPDATED_RISK_SCORE)
            .fraudStatus(UPDATED_FRAUD_STATUS)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);

        restTransactionRecordMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTransactionRecord.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedTransactionRecord))
            )
            .andExpect(status().isOk());

        // Validate the TransactionRecord in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTransactionRecordUpdatableFieldsEquals(
            partialUpdatedTransactionRecord,
            getPersistedTransactionRecord(partialUpdatedTransactionRecord)
        );
    }

    @Test
    @Transactional
    void patchNonExistingTransactionRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRecord.setId(longCount.incrementAndGet());

        // Create the TransactionRecord
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTransactionRecordMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, transactionRecordDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionRecordDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchTransactionRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRecord.setId(longCount.incrementAndGet());

        // Create the TransactionRecord
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionRecordMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(transactionRecordDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the TransactionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamTransactionRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        transactionRecord.setId(longCount.incrementAndGet());

        // Create the TransactionRecord
        TransactionRecordDTO transactionRecordDTO = transactionRecordMapper.toDto(transactionRecord);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTransactionRecordMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(transactionRecordDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the TransactionRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteTransactionRecord() throws Exception {
        // Initialize the database
        insertedTransactionRecord = transactionRecordRepository.saveAndFlush(transactionRecord);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the transactionRecord
        restTransactionRecordMockMvc
            .perform(delete(ENTITY_API_URL_ID, transactionRecord.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return transactionRecordRepository.count();
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

    protected TransactionRecord getPersistedTransactionRecord(TransactionRecord transactionRecord) {
        return transactionRecordRepository.findById(transactionRecord.getId()).orElseThrow();
    }

    protected void assertPersistedTransactionRecordToMatchAllProperties(TransactionRecord expectedTransactionRecord) {
        assertTransactionRecordAllPropertiesEquals(expectedTransactionRecord, getPersistedTransactionRecord(expectedTransactionRecord));
    }

    protected void assertPersistedTransactionRecordToMatchUpdatableProperties(TransactionRecord expectedTransactionRecord) {
        assertTransactionRecordAllUpdatablePropertiesEquals(
            expectedTransactionRecord,
            getPersistedTransactionRecord(expectedTransactionRecord)
        );
    }
}
