package com.zekodnix.zaramoney.web.rest;

import static com.zekodnix.zaramoney.domain.UserDetailsAccountAsserts.*;
import static com.zekodnix.zaramoney.web.rest.TestUtil.createUpdateProxyForBean;
import static com.zekodnix.zaramoney.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.UserDetailsAccount;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.service.UserDetailsAccountService;
import com.zekodnix.zaramoney.service.dto.UserDetailsAccountDTO;
import com.zekodnix.zaramoney.service.mapper.UserDetailsAccountMapper;
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
 * Integration tests for the {@link UserDetailsAccountResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class UserDetailsAccountResourceIT {

    private static final String DEFAULT_PHONE_NUMBER = "AAAAAAAAAA";
    private static final String UPDATED_PHONE_NUMBER = "BBBBBBBBBB";

    private static final String DEFAULT_FACE_PICTURE = "AAAAAAAAAA";
    private static final String UPDATED_FACE_PICTURE = "BBBBBBBBBB";

    private static final String DEFAULT_ID_CARD_PICTURE = "AAAAAAAAAA";
    private static final String UPDATED_ID_CARD_PICTURE = "BBBBBBBBBB";

    private static final String DEFAULT_COUNTRY = "AAAAAAAAAA";
    private static final String UPDATED_COUNTRY = "BBBBBBBBBB";

    private static final String DEFAULT_ADDRESS = "AAAAAAAAAA";
    private static final String UPDATED_ADDRESS = "BBBBBBBBBB";

    private static final Boolean DEFAULT_IS_AGENT = false;
    private static final Boolean UPDATED_IS_AGENT = true;

    private static final String DEFAULT_ACCOUNT_NUMBER = "AAAAAAAAAA";
    private static final String UPDATED_ACCOUNT_NUMBER = "BBBBBBBBBB";

    private static final BigDecimal DEFAULT_ACCOUNT_BALANCE = new BigDecimal(1);
    private static final BigDecimal UPDATED_ACCOUNT_BALANCE = new BigDecimal(2);
    private static final BigDecimal SMALLER_ACCOUNT_BALANCE = new BigDecimal(1 - 1);

    private static final String ENTITY_API_URL = "/api/user-details-accounts";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private UserDetailsAccountRepository userDetailsAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Mock
    private UserDetailsAccountRepository userDetailsAccountRepositoryMock;

    @Autowired
    private UserDetailsAccountMapper userDetailsAccountMapper;

    @Mock
    private UserDetailsAccountService userDetailsAccountServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restUserDetailsAccountMockMvc;

    private UserDetailsAccount userDetailsAccount;

    private UserDetailsAccount insertedUserDetailsAccount;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static UserDetailsAccount createEntity() {
        return new UserDetailsAccount()
            .phoneNumber(DEFAULT_PHONE_NUMBER)
            .facePicture(DEFAULT_FACE_PICTURE)
            .idCardPicture(DEFAULT_ID_CARD_PICTURE)
            .country(DEFAULT_COUNTRY)
            .address(DEFAULT_ADDRESS)
            .isAgent(DEFAULT_IS_AGENT)
            .accountNumber(DEFAULT_ACCOUNT_NUMBER)
            .accountBalance(DEFAULT_ACCOUNT_BALANCE);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static UserDetailsAccount createUpdatedEntity() {
        return new UserDetailsAccount()
            .phoneNumber(UPDATED_PHONE_NUMBER)
            .facePicture(UPDATED_FACE_PICTURE)
            .idCardPicture(UPDATED_ID_CARD_PICTURE)
            .country(UPDATED_COUNTRY)
            .address(UPDATED_ADDRESS)
            .isAgent(UPDATED_IS_AGENT)
            .accountNumber(UPDATED_ACCOUNT_NUMBER)
            .accountBalance(UPDATED_ACCOUNT_BALANCE);
    }

    @BeforeEach
    void initTest() {
        userDetailsAccount = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedUserDetailsAccount != null) {
            userDetailsAccountRepository.delete(insertedUserDetailsAccount);
            insertedUserDetailsAccount = null;
        }
    }

    @Test
    @Transactional
    void createUserDetailsAccount() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the UserDetailsAccount
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);
        var returnedUserDetailsAccountDTO = om.readValue(
            restUserDetailsAccountMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDetailsAccountDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            UserDetailsAccountDTO.class
        );

        // Validate the UserDetailsAccount in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedUserDetailsAccount = userDetailsAccountMapper.toEntity(returnedUserDetailsAccountDTO);
        assertUserDetailsAccountUpdatableFieldsEquals(
            returnedUserDetailsAccount,
            getPersistedUserDetailsAccount(returnedUserDetailsAccount)
        );

        insertedUserDetailsAccount = returnedUserDetailsAccount;
    }

    @Test
    @Transactional
    void createUserDetailsAccountWithExistingId() throws Exception {
        // Create the UserDetailsAccount with an existing ID
        userDetailsAccount.setId(1L);
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restUserDetailsAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDetailsAccountDTO)))
            .andExpect(status().isBadRequest());

        // Validate the UserDetailsAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkPhoneNumberIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        userDetailsAccount.setPhoneNumber(null);

        // Create the UserDetailsAccount, which fails.
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        restUserDetailsAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDetailsAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkFacePictureIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        userDetailsAccount.setFacePicture(null);

        // Create the UserDetailsAccount, which fails.
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        restUserDetailsAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDetailsAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkIdCardPictureIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        userDetailsAccount.setIdCardPicture(null);

        // Create the UserDetailsAccount, which fails.
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        restUserDetailsAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDetailsAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCountryIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        userDetailsAccount.setCountry(null);

        // Create the UserDetailsAccount, which fails.
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        restUserDetailsAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDetailsAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkAddressIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        userDetailsAccount.setAddress(null);

        // Create the UserDetailsAccount, which fails.
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        restUserDetailsAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDetailsAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkIsAgentIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        userDetailsAccount.setIsAgent(null);

        // Create the UserDetailsAccount, which fails.
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        restUserDetailsAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDetailsAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkAccountNumberIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        userDetailsAccount.setAccountNumber(null);

        // Create the UserDetailsAccount, which fails.
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        restUserDetailsAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDetailsAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkAccountBalanceIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        userDetailsAccount.setAccountBalance(null);

        // Create the UserDetailsAccount, which fails.
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        restUserDetailsAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDetailsAccountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccounts() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList
        restUserDetailsAccountMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(userDetailsAccount.getId().intValue())))
            .andExpect(jsonPath("$.[*].phoneNumber").value(hasItem(DEFAULT_PHONE_NUMBER)))
            .andExpect(jsonPath("$.[*].facePicture").value(hasItem(DEFAULT_FACE_PICTURE)))
            .andExpect(jsonPath("$.[*].idCardPicture").value(hasItem(DEFAULT_ID_CARD_PICTURE)))
            .andExpect(jsonPath("$.[*].country").value(hasItem(DEFAULT_COUNTRY)))
            .andExpect(jsonPath("$.[*].address").value(hasItem(DEFAULT_ADDRESS)))
            .andExpect(jsonPath("$.[*].isAgent").value(hasItem(DEFAULT_IS_AGENT)))
            .andExpect(jsonPath("$.[*].accountNumber").value(hasItem(DEFAULT_ACCOUNT_NUMBER)))
            .andExpect(jsonPath("$.[*].accountBalance").value(hasItem(sameNumber(DEFAULT_ACCOUNT_BALANCE))));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllUserDetailsAccountsWithEagerRelationshipsIsEnabled() throws Exception {
        when(userDetailsAccountServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restUserDetailsAccountMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(userDetailsAccountServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllUserDetailsAccountsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(userDetailsAccountServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restUserDetailsAccountMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(userDetailsAccountRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getUserDetailsAccount() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get the userDetailsAccount
        restUserDetailsAccountMockMvc
            .perform(get(ENTITY_API_URL_ID, userDetailsAccount.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(userDetailsAccount.getId().intValue()))
            .andExpect(jsonPath("$.phoneNumber").value(DEFAULT_PHONE_NUMBER))
            .andExpect(jsonPath("$.facePicture").value(DEFAULT_FACE_PICTURE))
            .andExpect(jsonPath("$.idCardPicture").value(DEFAULT_ID_CARD_PICTURE))
            .andExpect(jsonPath("$.country").value(DEFAULT_COUNTRY))
            .andExpect(jsonPath("$.address").value(DEFAULT_ADDRESS))
            .andExpect(jsonPath("$.isAgent").value(DEFAULT_IS_AGENT))
            .andExpect(jsonPath("$.accountNumber").value(DEFAULT_ACCOUNT_NUMBER))
            .andExpect(jsonPath("$.accountBalance").value(sameNumber(DEFAULT_ACCOUNT_BALANCE)));
    }

    @Test
    @Transactional
    void getUserDetailsAccountsByIdFiltering() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        Long id = userDetailsAccount.getId();

        defaultUserDetailsAccountFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultUserDetailsAccountFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultUserDetailsAccountFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByPhoneNumberIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where phoneNumber equals to
        defaultUserDetailsAccountFiltering("phoneNumber.equals=" + DEFAULT_PHONE_NUMBER, "phoneNumber.equals=" + UPDATED_PHONE_NUMBER);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByPhoneNumberIsInShouldWork() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where phoneNumber in
        defaultUserDetailsAccountFiltering(
            "phoneNumber.in=" + DEFAULT_PHONE_NUMBER + "," + UPDATED_PHONE_NUMBER,
            "phoneNumber.in=" + UPDATED_PHONE_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByPhoneNumberIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where phoneNumber is not null
        defaultUserDetailsAccountFiltering("phoneNumber.specified=true", "phoneNumber.specified=false");
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByPhoneNumberContainsSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where phoneNumber contains
        defaultUserDetailsAccountFiltering("phoneNumber.contains=" + DEFAULT_PHONE_NUMBER, "phoneNumber.contains=" + UPDATED_PHONE_NUMBER);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByPhoneNumberNotContainsSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where phoneNumber does not contain
        defaultUserDetailsAccountFiltering(
            "phoneNumber.doesNotContain=" + UPDATED_PHONE_NUMBER,
            "phoneNumber.doesNotContain=" + DEFAULT_PHONE_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByFacePictureIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where facePicture equals to
        defaultUserDetailsAccountFiltering("facePicture.equals=" + DEFAULT_FACE_PICTURE, "facePicture.equals=" + UPDATED_FACE_PICTURE);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByFacePictureIsInShouldWork() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where facePicture in
        defaultUserDetailsAccountFiltering(
            "facePicture.in=" + DEFAULT_FACE_PICTURE + "," + UPDATED_FACE_PICTURE,
            "facePicture.in=" + UPDATED_FACE_PICTURE
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByFacePictureIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where facePicture is not null
        defaultUserDetailsAccountFiltering("facePicture.specified=true", "facePicture.specified=false");
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByFacePictureContainsSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where facePicture contains
        defaultUserDetailsAccountFiltering("facePicture.contains=" + DEFAULT_FACE_PICTURE, "facePicture.contains=" + UPDATED_FACE_PICTURE);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByFacePictureNotContainsSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where facePicture does not contain
        defaultUserDetailsAccountFiltering(
            "facePicture.doesNotContain=" + UPDATED_FACE_PICTURE,
            "facePicture.doesNotContain=" + DEFAULT_FACE_PICTURE
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByIdCardPictureIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where idCardPicture equals to
        defaultUserDetailsAccountFiltering(
            "idCardPicture.equals=" + DEFAULT_ID_CARD_PICTURE,
            "idCardPicture.equals=" + UPDATED_ID_CARD_PICTURE
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByIdCardPictureIsInShouldWork() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where idCardPicture in
        defaultUserDetailsAccountFiltering(
            "idCardPicture.in=" + DEFAULT_ID_CARD_PICTURE + "," + UPDATED_ID_CARD_PICTURE,
            "idCardPicture.in=" + UPDATED_ID_CARD_PICTURE
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByIdCardPictureIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where idCardPicture is not null
        defaultUserDetailsAccountFiltering("idCardPicture.specified=true", "idCardPicture.specified=false");
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByIdCardPictureContainsSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where idCardPicture contains
        defaultUserDetailsAccountFiltering(
            "idCardPicture.contains=" + DEFAULT_ID_CARD_PICTURE,
            "idCardPicture.contains=" + UPDATED_ID_CARD_PICTURE
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByIdCardPictureNotContainsSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where idCardPicture does not contain
        defaultUserDetailsAccountFiltering(
            "idCardPicture.doesNotContain=" + UPDATED_ID_CARD_PICTURE,
            "idCardPicture.doesNotContain=" + DEFAULT_ID_CARD_PICTURE
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByCountryIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where country equals to
        defaultUserDetailsAccountFiltering("country.equals=" + DEFAULT_COUNTRY, "country.equals=" + UPDATED_COUNTRY);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByCountryIsInShouldWork() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where country in
        defaultUserDetailsAccountFiltering("country.in=" + DEFAULT_COUNTRY + "," + UPDATED_COUNTRY, "country.in=" + UPDATED_COUNTRY);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByCountryIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where country is not null
        defaultUserDetailsAccountFiltering("country.specified=true", "country.specified=false");
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByCountryContainsSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where country contains
        defaultUserDetailsAccountFiltering("country.contains=" + DEFAULT_COUNTRY, "country.contains=" + UPDATED_COUNTRY);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByCountryNotContainsSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where country does not contain
        defaultUserDetailsAccountFiltering("country.doesNotContain=" + UPDATED_COUNTRY, "country.doesNotContain=" + DEFAULT_COUNTRY);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAddressIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where address equals to
        defaultUserDetailsAccountFiltering("address.equals=" + DEFAULT_ADDRESS, "address.equals=" + UPDATED_ADDRESS);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAddressIsInShouldWork() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where address in
        defaultUserDetailsAccountFiltering("address.in=" + DEFAULT_ADDRESS + "," + UPDATED_ADDRESS, "address.in=" + UPDATED_ADDRESS);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAddressIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where address is not null
        defaultUserDetailsAccountFiltering("address.specified=true", "address.specified=false");
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAddressContainsSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where address contains
        defaultUserDetailsAccountFiltering("address.contains=" + DEFAULT_ADDRESS, "address.contains=" + UPDATED_ADDRESS);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAddressNotContainsSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where address does not contain
        defaultUserDetailsAccountFiltering("address.doesNotContain=" + UPDATED_ADDRESS, "address.doesNotContain=" + DEFAULT_ADDRESS);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByIsAgentIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where isAgent equals to
        defaultUserDetailsAccountFiltering("isAgent.equals=" + DEFAULT_IS_AGENT, "isAgent.equals=" + UPDATED_IS_AGENT);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByIsAgentIsInShouldWork() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where isAgent in
        defaultUserDetailsAccountFiltering("isAgent.in=" + DEFAULT_IS_AGENT + "," + UPDATED_IS_AGENT, "isAgent.in=" + UPDATED_IS_AGENT);
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByIsAgentIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where isAgent is not null
        defaultUserDetailsAccountFiltering("isAgent.specified=true", "isAgent.specified=false");
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAccountNumberIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where accountNumber equals to
        defaultUserDetailsAccountFiltering(
            "accountNumber.equals=" + DEFAULT_ACCOUNT_NUMBER,
            "accountNumber.equals=" + UPDATED_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAccountNumberIsInShouldWork() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where accountNumber in
        defaultUserDetailsAccountFiltering(
            "accountNumber.in=" + DEFAULT_ACCOUNT_NUMBER + "," + UPDATED_ACCOUNT_NUMBER,
            "accountNumber.in=" + UPDATED_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAccountNumberIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where accountNumber is not null
        defaultUserDetailsAccountFiltering("accountNumber.specified=true", "accountNumber.specified=false");
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAccountNumberContainsSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where accountNumber contains
        defaultUserDetailsAccountFiltering(
            "accountNumber.contains=" + DEFAULT_ACCOUNT_NUMBER,
            "accountNumber.contains=" + UPDATED_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAccountNumberNotContainsSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where accountNumber does not contain
        defaultUserDetailsAccountFiltering(
            "accountNumber.doesNotContain=" + UPDATED_ACCOUNT_NUMBER,
            "accountNumber.doesNotContain=" + DEFAULT_ACCOUNT_NUMBER
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAccountBalanceIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where accountBalance equals to
        defaultUserDetailsAccountFiltering(
            "accountBalance.equals=" + DEFAULT_ACCOUNT_BALANCE,
            "accountBalance.equals=" + UPDATED_ACCOUNT_BALANCE
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAccountBalanceIsInShouldWork() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where accountBalance in
        defaultUserDetailsAccountFiltering(
            "accountBalance.in=" + DEFAULT_ACCOUNT_BALANCE + "," + UPDATED_ACCOUNT_BALANCE,
            "accountBalance.in=" + UPDATED_ACCOUNT_BALANCE
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAccountBalanceIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where accountBalance is not null
        defaultUserDetailsAccountFiltering("accountBalance.specified=true", "accountBalance.specified=false");
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAccountBalanceIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where accountBalance is greater than or equal to
        defaultUserDetailsAccountFiltering(
            "accountBalance.greaterThanOrEqual=" + DEFAULT_ACCOUNT_BALANCE,
            "accountBalance.greaterThanOrEqual=" + UPDATED_ACCOUNT_BALANCE
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAccountBalanceIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where accountBalance is less than or equal to
        defaultUserDetailsAccountFiltering(
            "accountBalance.lessThanOrEqual=" + DEFAULT_ACCOUNT_BALANCE,
            "accountBalance.lessThanOrEqual=" + SMALLER_ACCOUNT_BALANCE
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAccountBalanceIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where accountBalance is less than
        defaultUserDetailsAccountFiltering(
            "accountBalance.lessThan=" + UPDATED_ACCOUNT_BALANCE,
            "accountBalance.lessThan=" + DEFAULT_ACCOUNT_BALANCE
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByAccountBalanceIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        // Get all the userDetailsAccountList where accountBalance is greater than
        defaultUserDetailsAccountFiltering(
            "accountBalance.greaterThan=" + SMALLER_ACCOUNT_BALANCE,
            "accountBalance.greaterThan=" + DEFAULT_ACCOUNT_BALANCE
        );
    }

    @Test
    @Transactional
    void getAllUserDetailsAccountsByUserLoginIsEqualToSomething() throws Exception {
        User userLogin;
        if (TestUtil.findAll(em, User.class).isEmpty()) {
            userDetailsAccountRepository.saveAndFlush(userDetailsAccount);
            userLogin = UserResourceIT.createEntity();
        } else {
            userLogin = TestUtil.findAll(em, User.class).get(0);
        }
        em.persist(userLogin);
        em.flush();
        userDetailsAccount.setUserLogin(userLogin);
        userDetailsAccountRepository.saveAndFlush(userDetailsAccount);
        Long userLoginId = userLogin.getId();
        // Get all the userDetailsAccountList where userLogin equals to userLoginId
        defaultUserDetailsAccountShouldBeFound("userLoginId.equals=" + userLoginId);

        // Get all the userDetailsAccountList where userLogin equals to (userLoginId + 1)
        defaultUserDetailsAccountShouldNotBeFound("userLoginId.equals=" + (userLoginId + 1));
    }

    private void defaultUserDetailsAccountFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultUserDetailsAccountShouldBeFound(shouldBeFound);
        defaultUserDetailsAccountShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultUserDetailsAccountShouldBeFound(String filter) throws Exception {
        restUserDetailsAccountMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(userDetailsAccount.getId().intValue())))
            .andExpect(jsonPath("$.[*].phoneNumber").value(hasItem(DEFAULT_PHONE_NUMBER)))
            .andExpect(jsonPath("$.[*].facePicture").value(hasItem(DEFAULT_FACE_PICTURE)))
            .andExpect(jsonPath("$.[*].idCardPicture").value(hasItem(DEFAULT_ID_CARD_PICTURE)))
            .andExpect(jsonPath("$.[*].country").value(hasItem(DEFAULT_COUNTRY)))
            .andExpect(jsonPath("$.[*].address").value(hasItem(DEFAULT_ADDRESS)))
            .andExpect(jsonPath("$.[*].isAgent").value(hasItem(DEFAULT_IS_AGENT)))
            .andExpect(jsonPath("$.[*].accountNumber").value(hasItem(DEFAULT_ACCOUNT_NUMBER)))
            .andExpect(jsonPath("$.[*].accountBalance").value(hasItem(sameNumber(DEFAULT_ACCOUNT_BALANCE))));

        // Check, that the count call also returns 1
        restUserDetailsAccountMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultUserDetailsAccountShouldNotBeFound(String filter) throws Exception {
        restUserDetailsAccountMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restUserDetailsAccountMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingUserDetailsAccount() throws Exception {
        // Get the userDetailsAccount
        restUserDetailsAccountMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingUserDetailsAccount() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the userDetailsAccount
        UserDetailsAccount updatedUserDetailsAccount = userDetailsAccountRepository.findById(userDetailsAccount.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedUserDetailsAccount are not directly saved in db
        em.detach(updatedUserDetailsAccount);
        updatedUserDetailsAccount
            .phoneNumber(UPDATED_PHONE_NUMBER)
            .facePicture(UPDATED_FACE_PICTURE)
            .idCardPicture(UPDATED_ID_CARD_PICTURE)
            .country(UPDATED_COUNTRY)
            .address(UPDATED_ADDRESS)
            .isAgent(UPDATED_IS_AGENT)
            .accountNumber(UPDATED_ACCOUNT_NUMBER)
            .accountBalance(UPDATED_ACCOUNT_BALANCE);
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(updatedUserDetailsAccount);

        restUserDetailsAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, userDetailsAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(userDetailsAccountDTO))
            )
            .andExpect(status().isOk());

        // Validate the UserDetailsAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedUserDetailsAccountToMatchAllProperties(updatedUserDetailsAccount);
    }

    @Test
    @Transactional
    void putNonExistingUserDetailsAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userDetailsAccount.setId(longCount.incrementAndGet());

        // Create the UserDetailsAccount
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restUserDetailsAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, userDetailsAccountDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(userDetailsAccountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the UserDetailsAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchUserDetailsAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userDetailsAccount.setId(longCount.incrementAndGet());

        // Create the UserDetailsAccount
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUserDetailsAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(userDetailsAccountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the UserDetailsAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamUserDetailsAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userDetailsAccount.setId(longCount.incrementAndGet());

        // Create the UserDetailsAccount
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUserDetailsAccountMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDetailsAccountDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the UserDetailsAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateUserDetailsAccountWithPatch() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the userDetailsAccount using partial update
        UserDetailsAccount partialUpdatedUserDetailsAccount = new UserDetailsAccount();
        partialUpdatedUserDetailsAccount.setId(userDetailsAccount.getId());

        partialUpdatedUserDetailsAccount
            .facePicture(UPDATED_FACE_PICTURE)
            .isAgent(UPDATED_IS_AGENT)
            .accountNumber(UPDATED_ACCOUNT_NUMBER)
            .accountBalance(UPDATED_ACCOUNT_BALANCE);

        restUserDetailsAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedUserDetailsAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedUserDetailsAccount))
            )
            .andExpect(status().isOk());

        // Validate the UserDetailsAccount in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertUserDetailsAccountUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedUserDetailsAccount, userDetailsAccount),
            getPersistedUserDetailsAccount(userDetailsAccount)
        );
    }

    @Test
    @Transactional
    void fullUpdateUserDetailsAccountWithPatch() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the userDetailsAccount using partial update
        UserDetailsAccount partialUpdatedUserDetailsAccount = new UserDetailsAccount();
        partialUpdatedUserDetailsAccount.setId(userDetailsAccount.getId());

        partialUpdatedUserDetailsAccount
            .phoneNumber(UPDATED_PHONE_NUMBER)
            .facePicture(UPDATED_FACE_PICTURE)
            .idCardPicture(UPDATED_ID_CARD_PICTURE)
            .country(UPDATED_COUNTRY)
            .address(UPDATED_ADDRESS)
            .isAgent(UPDATED_IS_AGENT)
            .accountNumber(UPDATED_ACCOUNT_NUMBER)
            .accountBalance(UPDATED_ACCOUNT_BALANCE);

        restUserDetailsAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedUserDetailsAccount.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedUserDetailsAccount))
            )
            .andExpect(status().isOk());

        // Validate the UserDetailsAccount in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertUserDetailsAccountUpdatableFieldsEquals(
            partialUpdatedUserDetailsAccount,
            getPersistedUserDetailsAccount(partialUpdatedUserDetailsAccount)
        );
    }

    @Test
    @Transactional
    void patchNonExistingUserDetailsAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userDetailsAccount.setId(longCount.incrementAndGet());

        // Create the UserDetailsAccount
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restUserDetailsAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, userDetailsAccountDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(userDetailsAccountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the UserDetailsAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchUserDetailsAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userDetailsAccount.setId(longCount.incrementAndGet());

        // Create the UserDetailsAccount
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUserDetailsAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(userDetailsAccountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the UserDetailsAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamUserDetailsAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userDetailsAccount.setId(longCount.incrementAndGet());

        // Create the UserDetailsAccount
        UserDetailsAccountDTO userDetailsAccountDTO = userDetailsAccountMapper.toDto(userDetailsAccount);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUserDetailsAccountMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(userDetailsAccountDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the UserDetailsAccount in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteUserDetailsAccount() throws Exception {
        // Initialize the database
        insertedUserDetailsAccount = userDetailsAccountRepository.saveAndFlush(userDetailsAccount);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the userDetailsAccount
        restUserDetailsAccountMockMvc
            .perform(delete(ENTITY_API_URL_ID, userDetailsAccount.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return userDetailsAccountRepository.count();
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

    protected UserDetailsAccount getPersistedUserDetailsAccount(UserDetailsAccount userDetailsAccount) {
        return userDetailsAccountRepository.findById(userDetailsAccount.getId()).orElseThrow();
    }

    protected void assertPersistedUserDetailsAccountToMatchAllProperties(UserDetailsAccount expectedUserDetailsAccount) {
        assertUserDetailsAccountAllPropertiesEquals(expectedUserDetailsAccount, getPersistedUserDetailsAccount(expectedUserDetailsAccount));
    }

    protected void assertPersistedUserDetailsAccountToMatchUpdatableProperties(UserDetailsAccount expectedUserDetailsAccount) {
        assertUserDetailsAccountAllUpdatablePropertiesEquals(
            expectedUserDetailsAccount,
            getPersistedUserDetailsAccount(expectedUserDetailsAccount)
        );
    }
}
