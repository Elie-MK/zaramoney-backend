package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.Currency;
import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.service.dto.*;
import com.zekodnix.zaramoney.service.exception.UserDetailsAccountNotFoundException;
import com.zekodnix.zaramoney.service.mapper.UserMapper;
import com.zekodnix.zaramoney.web.rest.vm.UserAccountVM;
import java.util.Map;
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
        UploadFileService uploadFileService,
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
        var currentUser = userService.getUserWithAuthorities().orElseThrow();
        var userSaved = userMapper.userToUserDTO(currentUser);

        var usdAccount = bankAccountServicePlus.createBankAccount(userSaved);

        Map<String, Map<String, String>> files = cloudinaryService.getUploadedPictures(userAccountVM);

        var userDetails = createUserDetails(userAccountVM, userSaved, files);

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
        Map<String, Map<String, String>> files
    ) {
        var userDetailsAccountDTO = new UserDetailsAccountDTO();
        userDetailsAccountDTO.setUser(userSaved);
        userDetailsAccountDTO.setFacePicture(files.containsKey("facePicture") ? files.get("facePicture").get("url") : null);
        userDetailsAccountDTO.setIdCardPicture(files.containsKey("idCardPicture") ? files.get("idCardPicture").get("url") : null);
        userDetailsAccountDTO.setIsAgent(false);
        userDetailsAccountDTO.setCountry(userAccountVM.getCountry());
        userDetailsAccountDTO.setAddress(userAccountVM.getAddress());
        userDetailsAccountDTO.setPhoneNumber(userAccountVM.getPhoneNumber());
        userDetailsAccountDTO.setKycStatus(KycStatus.VERIFIED);
        userDetailsAccountDTO.setExpoPushToken(userAccountVM.getExpoPushToken());
        return userDetailsAccountService.save(userDetailsAccountDTO);
    }

    public UserAccountDto getUserDetailsAccount() {
        var currentUser = userService.getUserWithAuthorities().orElseThrow();
        var userDetails = userDetailsAccountService.findByUserEmail(currentUser.getEmail()).orElseThrow();

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
            usdBankAccount,
            userDetails.getPhoneNumber(),
            userDetails.getKycStatus()
        );
    }
}
