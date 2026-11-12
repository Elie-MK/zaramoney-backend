package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.service.TransactionPinService;
import com.zekodnix.zaramoney.service.UserService;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import com.zekodnix.zaramoney.web.rest.vm.SetPinVM;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Transaction PIN of the current user. */
@RestController
@RequestMapping("/api/plus/security/pin")
public class TransactionPinResource {

    private final TransactionPinService transactionPinService;
    private final UserService userService;

    public TransactionPinResource(TransactionPinService transactionPinService, UserService userService) {
        this.transactionPinService = transactionPinService;
        this.userService = userService;
    }

    /** {@code GET} → {@code {"pinSet": true|false}}; the app asks the user to create a PIN when false. */
    @GetMapping
    public ResponseEntity<Map<String, Boolean>> status() {
        var user = currentUser();
        return ResponseEntity.ok(Map.of("pinSet", transactionPinService.isPinSet(user.getId())));
    }

    /** {@code PUT {"password": "...", "pin": "4826"}} → 204. Sets or changes the PIN and clears any PIN lock. */
    @PutMapping
    public ResponseEntity<Void> setPin(@Valid @RequestBody SetPinVM vm) {
        transactionPinService.setPin(currentUser(), vm.getPassword(), vm.getPin());
        return ResponseEntity.noContent().build();
    }

    private com.zekodnix.zaramoney.domain.User currentUser() {
        return userService
            .getUserWithAuthorities()
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated", "unauthorized"));
    }
}
