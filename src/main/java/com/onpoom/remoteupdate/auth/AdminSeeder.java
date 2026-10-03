package com.onpoom.remoteupdate.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.onpoom.remoteupdate.config.AppProperties;
import com.onpoom.remoteupdate.user.AppUser;
import com.onpoom.remoteupdate.user.AppUserRepository;
import com.onpoom.remoteupdate.user.Role;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 관리자 계정이 하나도 없으면 app.seed.admin 설정으로 최초 관리자 생성 (첫 로그인 시 비밀번호 변경 강제) */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        AppProperties.Admin admin = appProperties.seed().admin();
        if (userRepository.existsByEmail(admin.email())) {
            log.warn("시드 관리자 이메일({})이 다른 역할 계정으로 이미 존재해 생성하지 않습니다.", admin.email());
            return;
        }
        userRepository.save(AppUser.create(admin.email(), passwordEncoder.encode(admin.password()),
                admin.name(), Role.ADMIN));
        log.info("최초 관리자 계정을 생성했습니다: {} (첫 로그인 시 비밀번호 변경 필요)", admin.email());
    }
}
