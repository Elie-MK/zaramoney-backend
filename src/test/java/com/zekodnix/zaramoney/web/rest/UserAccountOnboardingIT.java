package com.zekodnix.zaramoney.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.repository.BankAccountRepository;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.repository.UserRepository;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.service.SignupBonusService;
import com.zekodnix.zaramoney.service.SystemAccountService;
import com.zekodnix.zaramoney.service.UploadFileService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Onboarding (TODO.md item 4 and 9): one account per user, KYC PENDING + admin review, $5 bonus through the ledger. */
@IntegrationTest
@AutoConfigureMockMvc
class UserAccountOnboardingIT {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private UserDetailsAccountRepository userDetailsAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private UploadFileService uploadFileService;

    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void mockCloudinary() {
        when(uploadFileService.uploadPrivateImage(any())).thenReturn("kyc/test-doc.png");
        when(uploadFileService.signedUrl(anyString())).thenReturn("https://signed.example/kyc");
    }

    @Test
    void onboardingCreatesOneAccountWithLedgerBonusAndPendingKyc() throws Exception {
        User u = plainUser();
        BigDecimal promoBefore = promoBalance();

        createAccount(u, png("face.png"), png("id.png"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kycStatus").value("PENDING"))
            .andExpect(jsonPath("$.usdAccount.balance").value(5.0));

        var account = bankAccountRepository.findAll().stream().filter(a -> a.getUser() != null && a.getUser().getId().equals(u.getId())).toList();
        assertThat(account).hasSize(1);
        assertThat(account.get(0).getBalance()).isEqualByComparingTo(SignupBonusService.BONUS_AMOUNT);
        assertThat(promoBalance()).isEqualByComparingTo(promoBefore.subtract(SignupBonusService.BONUS_AMOUNT));

        // balanced ledger for the bonus: one DEBIT on promo, one CREDIT on the customer
        List<String> entries = jdbcTemplate.queryForList(
            "select le.entry_type from ledger_entry le join transaction_record t on t.id = le.transaction_id " +
            "where t.receiver_id = ? and t.transaction_type = 'BONUS' order by le.entry_type",
            String.class,
            account.get(0).getId()
        );
        assertThat(entries).containsExactly("CREDIT", "DEBIT");

        // documents are stored as private references, not public URLs
        var details = userDetailsAccountRepository.findByUserEmail(u.getEmail()).orElseThrow();
        assertThat(details.getFacePicture()).isEqualTo("kyc/test-doc.png");

        // second submission is refused before anything is uploaded again
        createAccount(u, png("face.png"), png("id.png"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("error.accountalreadyexists"));
        verify(uploadFileService, times(2)).uploadPrivateImage(any());
        assertThat(promoBalance()).isEqualByComparingTo(promoBefore.subtract(SignupBonusService.BONUS_AMOUNT));
    }

    @Test
    void svgAndMissingDocumentsAreRejectedBeforeUpload() throws Exception {
        User u = plainUser();
        MockMultipartFile svg = new MockMultipartFile("idCardPicture", "id.svg", "image/svg", "<svg onload=alert(1)>".getBytes());

        createAccount(u, png("face.png"), svg).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.invalidimagetype"));
        createAccount(u, png("face.png"), null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.kycdocumentsrequired"));
        verify(uploadFileService, times(0)).uploadPrivateImage(any());
    }

    @Test
    void adminApprovesKyc() throws Exception {
        User u = plainUser();
        createAccount(u, png("face.png"), png("id.png")).andExpect(status().isOk());
        Long detailsId = userDetailsAccountRepository.findByUserEmail(u.getEmail()).orElseThrow().getId();

        // a customer cannot use the back office
        mockMvc.perform(post("/api/admin/backoffice/kyc/" + detailsId + "/approve").with(user(u.getLogin()))).andExpect(status().isForbidden());

        mockMvc
            .perform(get("/api/admin/backoffice/kyc/" + detailsId).with(user("admin").authorities(() -> AuthoritiesConstants.ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kycStatus").value("PENDING"))
            .andExpect(jsonPath("$.facePictureUrl").value("https://signed.example/kyc"));

        mockMvc
            .perform(post("/api/admin/backoffice/kyc/" + detailsId + "/approve").with(user("admin").authorities(() -> AuthoritiesConstants.ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kycStatus").value("VERIFIED"));
        assertThat(userDetailsAccountRepository.findById(detailsId).orElseThrow().getKycStatus()).isEqualTo(KycStatus.VERIFIED);
    }

    // ------------------------------------------------------------------------------------------ helpers

    private ResultActions createAccount(User u, MockMultipartFile face, MockMultipartFile idCard) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/user-account/create");
        if (face != null) request.file(new MockMultipartFile("facePicture", face.getOriginalFilename(), face.getContentType(), face.getBytes()));
        if (idCard != null) request.file(
            new MockMultipartFile("idCardPicture", idCard.getOriginalFilename(), idCard.getContentType(), idCard.getBytes())
        );
        request
            .param("phoneNumber", "+243000000000")
            .param("country", "CD")
            .param("address", "Kinshasa")
            .param("expoPushToken", "ExponentPushToken[test]");
        return mockMvc.perform(request.with(user(u.getLogin())));
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG);
    }

    private BigDecimal promoBalance() {
        return bankAccountRepository.findByAccountNumber(SystemAccountService.PROMO_ACCOUNT_NUMBER).orElseThrow().getBalance();
    }

    private User plainUser() {
        String login = "it-" + UUID.randomUUID().toString().substring(0, 12) + "@zaramoney.test";
        User user = new User();
        user.setLogin(login);
        user.setEmail(login);
        user.setPassword("$2a$10$" + "x".repeat(53));
        user.setActivated(true);
        user.setFirstName("New");
        user.setLastName("Customer");
        user.setLangKey("en");
        user = userRepository.saveAndFlush(user);
        createdUserIds.add(user.getId());
        return user;
    }

    @AfterEach
    void cleanUp() {
        // The test pool uses auto-commit=false: run the deletes in a transaction or they are rolled back
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> createdUserIds.forEach(this::deleteUserData));
        createdUserIds.clear();
    }

    private void deleteUserData(Long userId) {
        String accounts = "(select id from bank_account where user_id = " + userId + ")";
        String txs = "(select id from transaction_record where sender_id in " + accounts + " or receiver_id in " + accounts + ")";
        jdbcTemplate.update("delete from notification where user_id = ? or transaction_id in " + txs, userId);
        jdbcTemplate.execute(MoneyTestData.LEDGER_MAINTENANCE);
        jdbcTemplate.update("delete from ledger_entry where transaction_id in " + txs);
        jdbcTemplate.update("delete from transaction_fee where transaction_id in " + txs);
        jdbcTemplate.update("delete from transaction_record where id in " + txs);
        jdbcTemplate.update("delete from transaction_limit where account_id in " + accounts);
        jdbcTemplate.update("delete from user_details_account where user_id = ?", userId);
        jdbcTemplate.update("delete from bank_account where user_id = ?", userId);
        jdbcTemplate.update("delete from jhi_user_authority where user_id = ?", userId);
        jdbcTemplate.update("delete from jhi_user where id = ?", userId);
    }
}
