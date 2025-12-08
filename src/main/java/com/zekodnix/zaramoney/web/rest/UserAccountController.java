package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.service.UserAccountService;
import com.zekodnix.zaramoney.service.dto.UserAccountDto;
import com.zekodnix.zaramoney.web.rest.vm.UserAccountVM;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user-account")
public class UserAccountController {

    private final UserAccountService userAccountService;

    public UserAccountController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserAccountDto> createUserAccount(@Valid @ModelAttribute UserAccountVM userAccountVM) {
        var response = userAccountService.createNewUserAccount(userAccountVM);
        return ResponseEntity.ok(response);
    }
}
