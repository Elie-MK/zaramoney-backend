package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.User;
import com.zekodnix.zaramoney.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserServicePlus {

    private final UserRepository userRepository;

    public UserServicePlus(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findOneByLogin(String login) {
        return userRepository.findOneByLogin(login).orElse(null);
    }
}
