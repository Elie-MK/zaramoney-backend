package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.service.dto.UserAccountDto;
import com.zekodnix.zaramoney.service.dto.UserDetailsAccountDTO;
import com.zekodnix.zaramoney.service.exception.UserDetailsAccountNotFoundException;
import com.zekodnix.zaramoney.service.mapper.UserMapper;
import com.zekodnix.zaramoney.service.utils.MaskingUtils;
import com.zekodnix.zaramoney.web.rest.vm.UserAccountVM;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UserAccountService {

    private final UserService userService;
    private final UserDetailsAccountService userDetailsAccountService;
    private final UserMapper userMapper;
    private final UploadFileService uploadFileService;

    public UserAccountService(
        UserService userService,
        UserDetailsAccountService userDetailsAccountService,
        UserMapper userMapper,
        UploadFileService uploadFileService
    ) {
        this.userService = userService;
        this.userDetailsAccountService = userDetailsAccountService;
        this.userMapper = userMapper;
        this.uploadFileService = uploadFileService;
    }

    public UserAccountDto createNewUserAccount(UserAccountVM userAccountVM) {
        Map<String, Map<String, String>> files = getUploadedPictures(userAccountVM);
        var currentUser = userService.getUserWithAuthorities().orElseThrow();
        var userSaved = userMapper.userToUserDTO(currentUser);

        var userDetailsAccountDTO = new UserDetailsAccountDTO();
        userDetailsAccountDTO.setUserLogin(userSaved);
        userDetailsAccountDTO.setFacePicture(files.containsKey("facePicture") ? files.get("facePicture").get("url") : null);
        userDetailsAccountDTO.setIdCardPicture(files.containsKey("idCardPicture") ? files.get("idCardPicture").get("url") : null);
        userDetailsAccountDTO.setAccountBalance(BigDecimal.valueOf(5.00));
        userDetailsAccountDTO.setAccountNumber(generateAccountNumber().toString());
        userDetailsAccountDTO.setIsAgent(false);
        userDetailsAccountDTO.setCountry(userAccountVM.getCountry());
        userDetailsAccountDTO.setAddress(userAccountVM.getAddress());
        userDetailsAccountDTO.setPhoneNumber(userAccountVM.getPhoneNumber());
        var userDetails = userDetailsAccountService.save(userDetailsAccountDTO);

        return new UserAccountDto(
            currentUser.getFirstName(),
            currentUser.getLastName(),
            userDetails.getAccountNumber(),
            userDetails.getAccountBalance()
        );
    }

    public UserAccountDto getUserDetailsAccount() {
        var currentUser = userService.getUserWithAuthorities().orElseThrow();
        var userSaved = userMapper.userToUserDTO(currentUser);
        var userDetails = userDetailsAccountService
            .findByUserLoginId(userSaved.getId())
            .orElseThrow(() -> new UserDetailsAccountNotFoundException(userSaved.getId()));

        String maskedAccountNumber = MaskingUtils.maskAccountNumber(userDetails.getAccountNumber());

        return new UserAccountDto(
            currentUser.getFirstName(),
            currentUser.getLastName(),
            maskedAccountNumber,
            userDetails.getAccountBalance()
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
