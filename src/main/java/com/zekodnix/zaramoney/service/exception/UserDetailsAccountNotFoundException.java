package com.zekodnix.zaramoney.service.exception;

public class UserDetailsAccountNotFoundException extends RuntimeException {

    public UserDetailsAccountNotFoundException(Long userId) {
        super("User details account not found for user with id: " + userId);
    }

    public UserDetailsAccountNotFoundException(String message) {
        super(message);
    }
}
