package com.zekodnix.zaramoney.service;

import org.springframework.http.HttpStatus;
import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.service.dto.*;
import com.zekodnix.zaramoney.service.mapper.UserMapper;
import com.zekodnix.zaramoney.web.rest.vm.UserAccountVM;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private final UserService userService;
    private final UserDetailsAccountService userDetailsAccountService;
    private final UserMapper userMapper;
    private final BankAccountService bankAccountService;
    private final BankAccountServicePlus bankAccountServicePlus;
    private final CloudinaryService cloudinaryService;

    public UserAccountService(
        UserService userService,
        UserDetailsAccountService userDetailsAccountService,
        UserMapper userMapper,
        BankAccountService bankAccountService,
        BankAccountServicePlus bankAccountServicePlus,
        CloudinaryService cloudinaryService
    ) {
        this.userService = userService;
        this.userDetailsAccountService = userDetailsAccountService;
        this.userMapper = userMapper;
        this.cloudinaryService = cloudinaryService;
        this.bankAccountService = bankAccountService;
        this.bankAccountServicePlus = bankAccountServicePlus;
    }

    @Transactional
    public UserAccountDto createNewUserAccount(UserAccountVM userAccountVM) {
        var currentUser = userService
            .getUserWithAuthorities()
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated", "unauthorized"));

        // One wallet + one KYC record per user: re-submitting must not create extra accounts (and extra bonuses)
        if (
            userDetailsAccountService.findByUserEmail(currentUser.getEmail()).isPresent() ||
            !bankAccountService.findByUserIsCurrentUser().isEmpty()
        ) {
            throw ApiException.conflict("An account already exists for this user", "accountalreadyexists");
        }

        var userSaved = userMapper.userToUserDTO(currentUser);

        // Upload first (validates both images); uploads are deleted again if this transaction rolls back
        var documents = cloudinaryService.uploadKycDocuments(userAccountVM);

        var usdAccount = bankAccountServicePlus.createBankAccount(userSaved);

        var userDetails = createUserDetails(userAccountVM, userSaved, documents);

        return new UserAccountDto(
            currentUser.getFirstName(),
            currentUser.getLastName(),
            currentUser.getEmail(),
            usdAccount,
            userDetails.getPhoneNumber(),
            userDetails.getKycStatus()
        );
    }

    private UserDetailsAccountDTO createUserDetails(
        UserAccountVM userAccountVM,
        UserDTO userSaved,
        CloudinaryService.KycDocuments documents
    ) {
        var userDetailsAccountDTO = new UserDetailsAccountDTO();
        userDetailsAccountDTO.setUser(userSaved);
        // Private Cloudinary references ("<publicId>.<format>"), not public URLs
        userDetailsAccountDTO.setFacePicture(documents.facePicture());
        userDetailsAccountDTO.setIdCardPicture(documents.idCardPicture());
        userDetailsAccountDTO.setIsAgent(false);
        userDetailsAccountDTO.setCountry(userAccountVM.getCountry());
        userDetailsAccountDTO.setAddress(userAccountVM.getAddress());
        userDetailsAccountDTO.setPhoneNumber(userAccountVM.getPhoneNumber());
        // An admin must review the documents (POST /api/admin/backoffice/kyc/{id}/approve) before the user can send money
        userDetailsAccountDTO.setKycStatus(KycStatus.PENDING);
        userDetailsAccountDTO.setExpoPushToken(userAccountVM.getExpoPushToken());
        return userDetailsAccountService.save(userDetailsAccountDTO);
    }

    public UserAccountDto getUserDetailsAccount() {
        var currentUser = userService
            .getUserWithAuthorities()
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated", "unauthorized"));
        var userDetails = userDetailsAccountService
            .findByUserEmail(currentUser.getEmail())
            .orElseThrow(() -> ApiException.notFound("You don't have an account yet", "noaccount"));

        var usdBankAccount = bankAccountService
            .findByUserIsCurrentUser()
            .stream()
            .filter(account -> account.getCurrency() == Currency.USD)
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("You don't have an account yet", "noaccount"));

        return new UserAccountDto(
            currentUser.getFirstName(),
            currentUser.getLastName(),
            currentUser.getEmail(),
            usdBankAccount,
            userDetails.getPhoneNumber(),
            userDetails.getKycStatus()
        );
    }
}
