package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.repository.UserRepository;

public class UserServicePlus {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserServicePlus(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    public User findOneByLogin(String login) {
        return userRepository.findOneByLogin(login).orElse(null);
    }
}
