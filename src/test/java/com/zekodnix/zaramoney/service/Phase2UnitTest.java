package com.zekodnix.zaramoney.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zekodnix.zaramoney.domain.BankAccount;
import com.zekodnix.zaramoney.service.dto.LockedAccountsDTO;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import com.zekodnix.zaramoney.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

/** Unit tests for the Phase 2 rules that don't need a database. */
class Phase2UnitTest {

    // --- Item 3: fee is part of the balance check -------------------------------------------------

    private final TransactionValidatorService validator = new TransactionValidatorService(null, null, null);

    private static LockedAccountsDTO accounts(String balance) {
        return new LockedAccountsDTO(new BankAccount().balance(new BigDecimal(balance)), new BankAccount().balance(BigDecimal.ZERO));
    }

    @Test
    void balanceCheckIncludesTheFee() {
        // Old check: 1006 - 1001 = 5 >= MIN_BALANCE -> accepted, then 1001 + 9.51 debited -> 4.49 / negative
        assertThatThrownBy(() -> validator.checkAmountValidation(new BigDecimal("1001"), new BigDecimal("9.51"), accounts("1006")))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("Insufficient balance");
    }

    @Test
    void minimumBalanceMustRemainAfterAmountAndFee() {
        // 1020 - (1001 + 9.51) = 9.49 >= 5 OK
        assertThatCode(() -> validator.checkAmountValidation(new BigDecimal("1001"), new BigDecimal("9.51"), accounts("1020"))).doesNotThrowAnyException();
        // 1013 - 1010.51 = 2.49 < 5
        assertThatThrownBy(() -> validator.checkAmountValidation(new BigDecimal("1001"), new BigDecimal("9.51"), accounts("1013")))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("minimum balance");
    }

    // --- Item 10: PIN strength --------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = { "1111", "1234", "4321", "987654", "000000", "12a4", "123", "1234567" })
    void weakOrMalformedPinsAreRejected(String pin) {
        assertThatThrownBy(() -> TransactionPinService.validatePinStrength(pin)).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "4826", "1357", "902871" })
    void reasonablePinsAreAccepted(String pin) {
        assertThatCode(() -> TransactionPinService.validatePinStrength(pin)).doesNotThrowAnyException();
    }

    // --- Item 9: uploads are validated by content, not by client-supplied type/name ---------------

    static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };
    static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16 };
    static final byte[] WEBP = { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P' };

    @Test
    void realImagesAreAccepted() {
        assertThat(UploadFileService.readValidatedImage(new MockMultipartFile("f", "a.png", "image/png", PNG))).isEqualTo(PNG);
        assertThat(UploadFileService.readValidatedImage(new MockMultipartFile("f", "a.jpg", "image/jpeg", JPEG))).isEqualTo(JPEG);
        assertThat(UploadFileService.readValidatedImage(new MockMultipartFile("f", "a.webp", "image/webp", WEBP))).isEqualTo(WEBP);
    }

    @Test
    void svgAndDisguisedFilesAreRejected() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> UploadFileService.readValidatedImage(new MockMultipartFile("f", "a.svg", "image/svg", svg)))
            .isInstanceOf(ApiException.class);
        // an HTML/script file pretending to be a PNG (name + content type)
        assertThatThrownBy(() ->
            UploadFileService.readValidatedImage(new MockMultipartFile("f", "../../etc/x.png", "image/png", "<script>".getBytes()))
        ).isInstanceOf(ApiException.class);
    }

    @Test
    void oversizedFilesAreRejected() {
        byte[] big = new byte[(int) UploadFileService.MAX_FILE_SIZE + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThatThrownBy(() -> UploadFileService.readValidatedImage(new MockMultipartFile("f", "a.png", "image/png", big))).isInstanceOf(
            ApiException.class
        );
    }

    // --- Item 11: account numbers -----------------------------------------------------------------

    @Test
    void accountNumbersHaveThePrefixAndLength() {
        for (int i = 0; i < 100; i++) {
            assertThat(BankAccountServicePlus.generateAccountNumber()).matches("2512\\d{8}");
        }
    }

    // --- Item 8: request hash ---------------------------------------------------------------------

    @Test
    void requestHashIgnoresBigDecimalScaleButNotValues() {
        String a = IdempotencyRecordServicePlus.requestHash("/send", "251200000001", new BigDecimal("50"), "x");
        String b = IdempotencyRecordServicePlus.requestHash("/send", "251200000001", new BigDecimal("50.00"), "x");
        String c = IdempotencyRecordServicePlus.requestHash("/send", "251200000001", new BigDecimal("51"), "x");
        assertThat(a).isEqualTo(b).isNotEqualTo(c);
    }
}
