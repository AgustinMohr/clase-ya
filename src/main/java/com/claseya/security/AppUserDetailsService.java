package com.claseya.security;

import com.claseya.model.User;
import com.claseya.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new AppUserDetails(user);
    }

    @Transactional(readOnly = true)
    public AppUserDetails loadUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new AppUserDetails(user);
    }
}
