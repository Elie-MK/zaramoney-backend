package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.domain.IdempotencyRecordAsserts.*;
import static com.zekodnix.zaramoney.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.domain.IdempotencyRecord;
import com.zekodnix.zaramoney.repository.IdempotencyRecordRepository;
import com.zekodnix.zaramoney.service.dto.IdempotencyRecordDTO;
import com.zekodnix.zaramoney.service.mapper.IdempotencyRecordMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link IdempotencyRecordResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class IdempotencyRecordResourceIT {

    private static final String DEFAULT_KEY_HASH = "AAAAAAAAAA";
    private static final String UPDATED_KEY_HASH = "BBBBBBBBBB";

    private static final String DEFAULT_ENDPOINT = "AAAAAAAAAA";
    private static final String UPDATED_ENDPOINT = "BBBBBBBBBB";

    private static final Long DEFAULT_USER_ID = 1L;
    private static final Long UPDATED_USER_ID = 2L;
    private static final Long SMALLER_USER_ID = 1L - 1L;

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_RESPONSE_BODY = "AAAAAAAAAA";
    private static final String UPDATED_RESPONSE_BODY = "BBBBBBBBBB";

    private static final Integer DEFAULT_RESPONSE_STATUS = 1;
    private static final Integer UPDATED_RESPONSE_STATUS = 2;
    private static final Integer SMALLER_RESPONSE_STATUS = 1 - 1;

    private static final String DEFAULT_TRANSACTION_REFERENCE = "AAAAAAAAAA";
    private static final String UPDATED_TRANSACTION_REFERENCE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/idempotency-records";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private IdempotencyRecordMapper idempotencyRecordMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restIdempotencyRecordMockMvc;

    private IdempotencyRecord idempotencyRecord;

    private IdempotencyRecord insertedIdempotencyRecord;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static IdempotencyRecord createEntity() {
        return new IdempotencyRecord()
            .keyHash(DEFAULT_KEY_HASH)
            .endpoint(DEFAULT_ENDPOINT)
            .userId(DEFAULT_USER_ID)
            .createdAt(DEFAULT_CREATED_AT)
            .responseBody(DEFAULT_RESPONSE_BODY)
            .responseStatus(DEFAULT_RESPONSE_STATUS)
            .transactionReference(DEFAULT_TRANSACTION_REFERENCE);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static IdempotencyRecord createUpdatedEntity() {
        return new IdempotencyRecord()
            .keyHash(UPDATED_KEY_HASH)
            .endpoint(UPDATED_ENDPOINT)
            .userId(UPDATED_USER_ID)
            .createdAt(UPDATED_CREATED_AT)
            .responseBody(UPDATED_RESPONSE_BODY)
            .responseStatus(UPDATED_RESPONSE_STATUS)
            .transactionReference(UPDATED_TRANSACTION_REFERENCE);
    }

    @BeforeEach
    void initTest() {
        idempotencyRecord = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedIdempotencyRecord != null) {
            idempotencyRecordRepository.delete(insertedIdempotencyRecord);
            insertedIdempotencyRecord = null;
        }
    }

    @Test
    @Transactional
    void createIdempotencyRecord() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the IdempotencyRecord
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(idempotencyRecord);
        var returnedIdempotencyRecordDTO = om.readValue(
            restIdempotencyRecordMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(idempotencyRecordDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            IdempotencyRecordDTO.class
        );

        // Validate the IdempotencyRecord in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedIdempotencyRecord = idempotencyRecordMapper.toEntity(returnedIdempotencyRecordDTO);
        assertIdempotencyRecordUpdatableFieldsEquals(returnedIdempotencyRecord, getPersistedIdempotencyRecord(returnedIdempotencyRecord));

        insertedIdempotencyRecord = returnedIdempotencyRecord;
    }

    @Test
    @Transactional
    void createIdempotencyRecordWithExistingId() throws Exception {
        // Create the IdempotencyRecord with an existing ID
        idempotencyRecord.setId(1L);
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(idempotencyRecord);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restIdempotencyRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(idempotencyRecordDTO)))
            .andExpect(status().isBadRequest());

        // Validate the IdempotencyRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkKeyHashIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        idempotencyRecord.setKeyHash(null);

        // Create the IdempotencyRecord, which fails.
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(idempotencyRecord);

        restIdempotencyRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(idempotencyRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkEndpointIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        idempotencyRecord.setEndpoint(null);

        // Create the IdempotencyRecord, which fails.
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(idempotencyRecord);

        restIdempotencyRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(idempotencyRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkUserIdIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        idempotencyRecord.setUserId(null);

        // Create the IdempotencyRecord, which fails.
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(idempotencyRecord);

        restIdempotencyRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(idempotencyRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        idempotencyRecord.setCreatedAt(null);

        // Create the IdempotencyRecord, which fails.
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(idempotencyRecord);

        restIdempotencyRecordMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(idempotencyRecordDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecords() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList
        restIdempotencyRecordMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(idempotencyRecord.getId().intValue())))
            .andExpect(jsonPath("$.[*].keyHash").value(hasItem(DEFAULT_KEY_HASH)))
            .andExpect(jsonPath("$.[*].endpoint").value(hasItem(DEFAULT_ENDPOINT)))
            .andExpect(jsonPath("$.[*].userId").value(hasItem(DEFAULT_USER_ID.intValue())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].responseBody").value(hasItem(DEFAULT_RESPONSE_BODY)))
            .andExpect(jsonPath("$.[*].responseStatus").value(hasItem(DEFAULT_RESPONSE_STATUS)))
            .andExpect(jsonPath("$.[*].transactionReference").value(hasItem(DEFAULT_TRANSACTION_REFERENCE)));
    }

    @Test
    @Transactional
    void getIdempotencyRecord() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get the idempotencyRecord
        restIdempotencyRecordMockMvc
            .perform(get(ENTITY_API_URL_ID, idempotencyRecord.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(idempotencyRecord.getId().intValue()))
            .andExpect(jsonPath("$.keyHash").value(DEFAULT_KEY_HASH))
            .andExpect(jsonPath("$.endpoint").value(DEFAULT_ENDPOINT))
            .andExpect(jsonPath("$.userId").value(DEFAULT_USER_ID.intValue()))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.responseBody").value(DEFAULT_RESPONSE_BODY))
            .andExpect(jsonPath("$.responseStatus").value(DEFAULT_RESPONSE_STATUS))
            .andExpect(jsonPath("$.transactionReference").value(DEFAULT_TRANSACTION_REFERENCE));
    }

    @Test
    @Transactional
    void getIdempotencyRecordsByIdFiltering() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        Long id = idempotencyRecord.getId();

        defaultIdempotencyRecordFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultIdempotencyRecordFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultIdempotencyRecordFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByKeyHashIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where keyHash equals to
        defaultIdempotencyRecordFiltering("keyHash.equals=" + DEFAULT_KEY_HASH, "keyHash.equals=" + UPDATED_KEY_HASH);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByKeyHashIsInShouldWork() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where keyHash in
        defaultIdempotencyRecordFiltering("keyHash.in=" + DEFAULT_KEY_HASH + "," + UPDATED_KEY_HASH, "keyHash.in=" + UPDATED_KEY_HASH);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByKeyHashIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where keyHash is not null
        defaultIdempotencyRecordFiltering("keyHash.specified=true", "keyHash.specified=false");
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByKeyHashContainsSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where keyHash contains
        defaultIdempotencyRecordFiltering("keyHash.contains=" + DEFAULT_KEY_HASH, "keyHash.contains=" + UPDATED_KEY_HASH);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByKeyHashNotContainsSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where keyHash does not contain
        defaultIdempotencyRecordFiltering("keyHash.doesNotContain=" + UPDATED_KEY_HASH, "keyHash.doesNotContain=" + DEFAULT_KEY_HASH);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByEndpointIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where endpoint equals to
        defaultIdempotencyRecordFiltering("endpoint.equals=" + DEFAULT_ENDPOINT, "endpoint.equals=" + UPDATED_ENDPOINT);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByEndpointIsInShouldWork() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where endpoint in
        defaultIdempotencyRecordFiltering("endpoint.in=" + DEFAULT_ENDPOINT + "," + UPDATED_ENDPOINT, "endpoint.in=" + UPDATED_ENDPOINT);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByEndpointIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where endpoint is not null
        defaultIdempotencyRecordFiltering("endpoint.specified=true", "endpoint.specified=false");
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByEndpointContainsSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where endpoint contains
        defaultIdempotencyRecordFiltering("endpoint.contains=" + DEFAULT_ENDPOINT, "endpoint.contains=" + UPDATED_ENDPOINT);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByEndpointNotContainsSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where endpoint does not contain
        defaultIdempotencyRecordFiltering("endpoint.doesNotContain=" + UPDATED_ENDPOINT, "endpoint.doesNotContain=" + DEFAULT_ENDPOINT);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByUserIdIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where userId equals to
        defaultIdempotencyRecordFiltering("userId.equals=" + DEFAULT_USER_ID, "userId.equals=" + UPDATED_USER_ID);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByUserIdIsInShouldWork() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where userId in
        defaultIdempotencyRecordFiltering("userId.in=" + DEFAULT_USER_ID + "," + UPDATED_USER_ID, "userId.in=" + UPDATED_USER_ID);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByUserIdIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where userId is not null
        defaultIdempotencyRecordFiltering("userId.specified=true", "userId.specified=false");
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByUserIdIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where userId is greater than or equal to
        defaultIdempotencyRecordFiltering("userId.greaterThanOrEqual=" + DEFAULT_USER_ID, "userId.greaterThanOrEqual=" + UPDATED_USER_ID);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByUserIdIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where userId is less than or equal to
        defaultIdempotencyRecordFiltering("userId.lessThanOrEqual=" + DEFAULT_USER_ID, "userId.lessThanOrEqual=" + SMALLER_USER_ID);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByUserIdIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where userId is less than
        defaultIdempotencyRecordFiltering("userId.lessThan=" + UPDATED_USER_ID, "userId.lessThan=" + DEFAULT_USER_ID);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByUserIdIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where userId is greater than
        defaultIdempotencyRecordFiltering("userId.greaterThan=" + SMALLER_USER_ID, "userId.greaterThan=" + DEFAULT_USER_ID);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where createdAt equals to
        defaultIdempotencyRecordFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where createdAt in
        defaultIdempotencyRecordFiltering(
            "createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT,
            "createdAt.in=" + UPDATED_CREATED_AT
        );
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where createdAt is not null
        defaultIdempotencyRecordFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByResponseStatusIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where responseStatus equals to
        defaultIdempotencyRecordFiltering(
            "responseStatus.equals=" + DEFAULT_RESPONSE_STATUS,
            "responseStatus.equals=" + UPDATED_RESPONSE_STATUS
        );
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByResponseStatusIsInShouldWork() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where responseStatus in
        defaultIdempotencyRecordFiltering(
            "responseStatus.in=" + DEFAULT_RESPONSE_STATUS + "," + UPDATED_RESPONSE_STATUS,
            "responseStatus.in=" + UPDATED_RESPONSE_STATUS
        );
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByResponseStatusIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where responseStatus is not null
        defaultIdempotencyRecordFiltering("responseStatus.specified=true", "responseStatus.specified=false");
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByResponseStatusIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where responseStatus is greater than or equal to
        defaultIdempotencyRecordFiltering(
            "responseStatus.greaterThanOrEqual=" + DEFAULT_RESPONSE_STATUS,
            "responseStatus.greaterThanOrEqual=" + UPDATED_RESPONSE_STATUS
        );
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByResponseStatusIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where responseStatus is less than or equal to
        defaultIdempotencyRecordFiltering(
            "responseStatus.lessThanOrEqual=" + DEFAULT_RESPONSE_STATUS,
            "responseStatus.lessThanOrEqual=" + SMALLER_RESPONSE_STATUS
        );
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByResponseStatusIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where responseStatus is less than
        defaultIdempotencyRecordFiltering(
            "responseStatus.lessThan=" + UPDATED_RESPONSE_STATUS,
            "responseStatus.lessThan=" + DEFAULT_RESPONSE_STATUS
        );
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByResponseStatusIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where responseStatus is greater than
        defaultIdempotencyRecordFiltering(
            "responseStatus.greaterThan=" + SMALLER_RESPONSE_STATUS,
            "responseStatus.greaterThan=" + DEFAULT_RESPONSE_STATUS
        );
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByTransactionReferenceIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where transactionReference equals to
        defaultIdempotencyRecordFiltering(
            "transactionReference.equals=" + DEFAULT_TRANSACTION_REFERENCE,
            "transactionReference.equals=" + UPDATED_TRANSACTION_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByTransactionReferenceIsInShouldWork() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where transactionReference in
        defaultIdempotencyRecordFiltering(
            "transactionReference.in=" + DEFAULT_TRANSACTION_REFERENCE + "," + UPDATED_TRANSACTION_REFERENCE,
            "transactionReference.in=" + UPDATED_TRANSACTION_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByTransactionReferenceIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where transactionReference is not null
        defaultIdempotencyRecordFiltering("transactionReference.specified=true", "transactionReference.specified=false");
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByTransactionReferenceContainsSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where transactionReference contains
        defaultIdempotencyRecordFiltering(
            "transactionReference.contains=" + DEFAULT_TRANSACTION_REFERENCE,
            "transactionReference.contains=" + UPDATED_TRANSACTION_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllIdempotencyRecordsByTransactionReferenceNotContainsSomething() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        // Get all the idempotencyRecordList where transactionReference does not contain
        defaultIdempotencyRecordFiltering(
            "transactionReference.doesNotContain=" + UPDATED_TRANSACTION_REFERENCE,
            "transactionReference.doesNotContain=" + DEFAULT_TRANSACTION_REFERENCE
        );
    }

    private void defaultIdempotencyRecordFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultIdempotencyRecordShouldBeFound(shouldBeFound);
        defaultIdempotencyRecordShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultIdempotencyRecordShouldBeFound(String filter) throws Exception {
        restIdempotencyRecordMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(idempotencyRecord.getId().intValue())))
            .andExpect(jsonPath("$.[*].keyHash").value(hasItem(DEFAULT_KEY_HASH)))
            .andExpect(jsonPath("$.[*].endpoint").value(hasItem(DEFAULT_ENDPOINT)))
            .andExpect(jsonPath("$.[*].userId").value(hasItem(DEFAULT_USER_ID.intValue())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].responseBody").value(hasItem(DEFAULT_RESPONSE_BODY)))
            .andExpect(jsonPath("$.[*].responseStatus").value(hasItem(DEFAULT_RESPONSE_STATUS)))
            .andExpect(jsonPath("$.[*].transactionReference").value(hasItem(DEFAULT_TRANSACTION_REFERENCE)));

        // Check, that the count call also returns 1
        restIdempotencyRecordMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultIdempotencyRecordShouldNotBeFound(String filter) throws Exception {
        restIdempotencyRecordMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restIdempotencyRecordMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingIdempotencyRecord() throws Exception {
        // Get the idempotencyRecord
        restIdempotencyRecordMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingIdempotencyRecord() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the idempotencyRecord
        IdempotencyRecord updatedIdempotencyRecord = idempotencyRecordRepository.findById(idempotencyRecord.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedIdempotencyRecord are not directly saved in db
        em.detach(updatedIdempotencyRecord);
        updatedIdempotencyRecord
            .keyHash(UPDATED_KEY_HASH)
            .endpoint(UPDATED_ENDPOINT)
            .userId(UPDATED_USER_ID)
            .createdAt(UPDATED_CREATED_AT)
            .responseBody(UPDATED_RESPONSE_BODY)
            .responseStatus(UPDATED_RESPONSE_STATUS)
            .transactionReference(UPDATED_TRANSACTION_REFERENCE);
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(updatedIdempotencyRecord);

        restIdempotencyRecordMockMvc
            .perform(
                put(ENTITY_API_URL_ID, idempotencyRecordDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(idempotencyRecordDTO))
            )
            .andExpect(status().isOk());

        // Validate the IdempotencyRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedIdempotencyRecordToMatchAllProperties(updatedIdempotencyRecord);
    }

    @Test
    @Transactional
    void putNonExistingIdempotencyRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        idempotencyRecord.setId(longCount.incrementAndGet());

        // Create the IdempotencyRecord
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(idempotencyRecord);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restIdempotencyRecordMockMvc
            .perform(
                put(ENTITY_API_URL_ID, idempotencyRecordDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(idempotencyRecordDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the IdempotencyRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchIdempotencyRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        idempotencyRecord.setId(longCount.incrementAndGet());

        // Create the IdempotencyRecord
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(idempotencyRecord);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restIdempotencyRecordMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(idempotencyRecordDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the IdempotencyRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamIdempotencyRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        idempotencyRecord.setId(longCount.incrementAndGet());

        // Create the IdempotencyRecord
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(idempotencyRecord);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restIdempotencyRecordMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(idempotencyRecordDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the IdempotencyRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateIdempotencyRecordWithPatch() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the idempotencyRecord using partial update
        IdempotencyRecord partialUpdatedIdempotencyRecord = new IdempotencyRecord();
        partialUpdatedIdempotencyRecord.setId(idempotencyRecord.getId());

        partialUpdatedIdempotencyRecord
            .keyHash(UPDATED_KEY_HASH)
            .endpoint(UPDATED_ENDPOINT)
            .userId(UPDATED_USER_ID)
            .responseBody(UPDATED_RESPONSE_BODY)
            .responseStatus(UPDATED_RESPONSE_STATUS);

        restIdempotencyRecordMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedIdempotencyRecord.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedIdempotencyRecord))
            )
            .andExpect(status().isOk());

        // Validate the IdempotencyRecord in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertIdempotencyRecordUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedIdempotencyRecord, idempotencyRecord),
            getPersistedIdempotencyRecord(idempotencyRecord)
        );
    }

    @Test
    @Transactional
    void fullUpdateIdempotencyRecordWithPatch() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the idempotencyRecord using partial update
        IdempotencyRecord partialUpdatedIdempotencyRecord = new IdempotencyRecord();
        partialUpdatedIdempotencyRecord.setId(idempotencyRecord.getId());

        partialUpdatedIdempotencyRecord
            .keyHash(UPDATED_KEY_HASH)
            .endpoint(UPDATED_ENDPOINT)
            .userId(UPDATED_USER_ID)
            .createdAt(UPDATED_CREATED_AT)
            .responseBody(UPDATED_RESPONSE_BODY)
            .responseStatus(UPDATED_RESPONSE_STATUS)
            .transactionReference(UPDATED_TRANSACTION_REFERENCE);

        restIdempotencyRecordMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedIdempotencyRecord.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedIdempotencyRecord))
            )
            .andExpect(status().isOk());

        // Validate the IdempotencyRecord in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertIdempotencyRecordUpdatableFieldsEquals(
            partialUpdatedIdempotencyRecord,
            getPersistedIdempotencyRecord(partialUpdatedIdempotencyRecord)
        );
    }

    @Test
    @Transactional
    void patchNonExistingIdempotencyRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        idempotencyRecord.setId(longCount.incrementAndGet());

        // Create the IdempotencyRecord
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(idempotencyRecord);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restIdempotencyRecordMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, idempotencyRecordDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(idempotencyRecordDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the IdempotencyRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchIdempotencyRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        idempotencyRecord.setId(longCount.incrementAndGet());

        // Create the IdempotencyRecord
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(idempotencyRecord);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restIdempotencyRecordMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(idempotencyRecordDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the IdempotencyRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamIdempotencyRecord() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        idempotencyRecord.setId(longCount.incrementAndGet());

        // Create the IdempotencyRecord
        IdempotencyRecordDTO idempotencyRecordDTO = idempotencyRecordMapper.toDto(idempotencyRecord);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restIdempotencyRecordMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(idempotencyRecordDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the IdempotencyRecord in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteIdempotencyRecord() throws Exception {
        // Initialize the database
        insertedIdempotencyRecord = idempotencyRecordRepository.saveAndFlush(idempotencyRecord);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the idempotencyRecord
        restIdempotencyRecordMockMvc
            .perform(delete(ENTITY_API_URL_ID, idempotencyRecord.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return idempotencyRecordRepository.count();
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

    protected IdempotencyRecord getPersistedIdempotencyRecord(IdempotencyRecord idempotencyRecord) {
        return idempotencyRecordRepository.findById(idempotencyRecord.getId()).orElseThrow();
    }

    protected void assertPersistedIdempotencyRecordToMatchAllProperties(IdempotencyRecord expectedIdempotencyRecord) {
        assertIdempotencyRecordAllPropertiesEquals(expectedIdempotencyRecord, getPersistedIdempotencyRecord(expectedIdempotencyRecord));
    }

    protected void assertPersistedIdempotencyRecordToMatchUpdatableProperties(IdempotencyRecord expectedIdempotencyRecord) {
        assertIdempotencyRecordAllUpdatablePropertiesEquals(
            expectedIdempotencyRecord,
            getPersistedIdempotencyRecord(expectedIdempotencyRecord)
        );
    }
}
