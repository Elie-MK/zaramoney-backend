package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.service.dto.BankAccountDTO;
import com.zekodnix.zaramoney.service.dto.UserAccountDto;
import com.zekodnix.zaramoney.service.dto.UserDTO;
import com.zekodnix.zaramoney.service.dto.UserDetailsAccountDTO;
import com.zekodnix.zaramoney.service.exception.UserDetailsAccountNotFoundException;
import com.zekodnix.zaramoney.service.mapper.UserMapper;
import com.zekodnix.zaramoney.service.utils.MaskingUtils;
import com.zekodnix.zaramoney.web.rest.vm.UserAccountVM;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private final UserService userService;
    private final UserDetailsAccountService userDetailsAccountService;
    private final UserMapper userMapper;
    private final UploadFileService uploadFileService;
    private final BankAccountService bankAccountService;

    public UserAccountService(
        UserService userService,
        UserDetailsAccountService userDetailsAccountService,
        UserMapper userMapper,
        UploadFileService uploadFileService,
        BankAccountService bankAccountService
    ) {
        this.userService = userService;
        this.userDetailsAccountService = userDetailsAccountService;
        this.userMapper = userMapper;
        this.uploadFileService = uploadFileService;
        this.bankAccountService = bankAccountService;
    }

    @Transactional
    public UserAccountDto createNewUserAccount(UserAccountVM userAccountVM) {
        var currentUser = userService.getUserWithAuthorities().orElseThrow();
        var userSaved = userMapper.userToUserDTO(currentUser);

        var tndAccount = createBankAccount(userSaved, Currency.TND);
        var usdAccount = createBankAccount(userSaved, Currency.USD);

        Map<String, Map<String, String>> files = getUploadedPictures(userAccountVM);

        createUserDetails(userAccountVM, userSaved, files);

        return new UserAccountDto(currentUser.getFirstName(), currentUser.getLastName(), currentUser.getEmail(), tndAccount, usdAccount);
    }

    private BankAccountDTO createBankAccount(UserDTO user, Currency currency) {
        var bankAccountDTO = new BankAccountDTO();
        bankAccountDTO.setUser(user);
        bankAccountDTO.setBalance(BigDecimal.valueOf(5.00));
        bankAccountDTO.setAccountNumber(generateAccountNumber().toString());
        bankAccountDTO.setCurrency(currency);

        return bankAccountService.save(bankAccountDTO);
    }

    private void createUserDetails(UserAccountVM userAccountVM, UserDTO userSaved, Map<String, Map<String, String>> files) {
        var userDetailsAccountDTO = new UserDetailsAccountDTO();
        userDetailsAccountDTO.setUser(userSaved);
        userDetailsAccountDTO.setFacePicture(files.containsKey("facePicture") ? files.get("facePicture").get("url") : null);
        userDetailsAccountDTO.setIdCardPicture(files.containsKey("idCardPicture") ? files.get("idCardPicture").get("url") : null);
        userDetailsAccountDTO.setIsAgent(false);
        userDetailsAccountDTO.setCountry(userAccountVM.getCountry());
        userDetailsAccountDTO.setAddress(userAccountVM.getAddress());
        userDetailsAccountDTO.setPhoneNumber(userAccountVM.getPhoneNumber());
        userDetailsAccountService.save(userDetailsAccountDTO);
    }

    public UserAccountDto getUserDetailsAccount() {
        var currentUser = userService.getUserWithAuthorities().orElseThrow();

        var tndBankAccount = bankAccountService
            .findByUserIsCurrentUser()
            .stream()
            .filter(account -> account.getCurrency() == Currency.TND)
            .findFirst()
            .orElseThrow(() -> new UserDetailsAccountNotFoundException("TND account not found for current user"));

        var usdBankAccount = bankAccountService
            .findByUserIsCurrentUser()
            .stream()
            .filter(account -> account.getCurrency() == Currency.USD)
            .findFirst()
            .orElseThrow(() -> new UserDetailsAccountNotFoundException("USD account not found for current user"));

        return new UserAccountDto(
            currentUser.getFirstName(),
            currentUser.getLastName(),
            currentUser.getEmail(),
            tndBankAccount,
            usdBankAccount
        );
    }

    private BigDecimal generateAccountNumber() {
        long randomPart = (long) (Math.random() * 100_000_000L);
        String accountNumberStr = "2512" + String.format("%08d", randomPart);
        return new BigDecimal(accountNumberStr);
    }

    private Map<String, Map<String, String>> getUploadedPictures(UserAccountVM userAccountVM) {
        Map<String, Map<String, String>> pictures = new HashMap<>();

        // Upload face picture
        if (userAccountVM.getFacePicture() != null) {
            Map<String, String> faceUpload = uploadFileService.uploadFile(userAccountVM.getFacePicture());
            validateUploadedFile(faceUpload);
            pictures.put("facePicture", faceUpload);
        }

        // Upload ID card picture
        if (userAccountVM.getIdCardPicture() != null) {
            Map<String, String> idCardUpload = uploadFileService.uploadFile(userAccountVM.getIdCardPicture());
            validateUploadedFile(idCardUpload);
            pictures.put("idCardPicture", idCardUpload);
        }

        if (pictures.isEmpty()) {
            throw new IllegalArgumentException("No valid image files found in UserAccountVM.");
        }

        return pictures;
    }

    private void validateUploadedFile(Map<String, String> file) {
        if (
            file == null ||
            !file.containsKey("url") ||
            !file.containsKey("publicId") ||
            file.get("url") == null ||
            file.get("publicId") == null
        ) {
            throw new IllegalStateException("Uploaded file missing url or publicId");
        }
    }
}
