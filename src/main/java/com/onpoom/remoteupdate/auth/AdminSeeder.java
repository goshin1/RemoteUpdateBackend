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
import com.onpoom.remoteupdate.user.UserAdminService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 계정이 하나도 없으면 app.seed.admin 설정으로 최초 관리자 생성 (첫 로그인 시 비밀번호 변경 강제).
 * <p>
 * ApplicationRunner: 스프링이 모든 빈을 준비한 직후, 앱 시작 마지막에 run() 을 한 번 호출한다.
 * 초기 데이터 넣기, 시작 점검 같은 일에 쓴다.
 */
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
        AppProperties.Admin admin = appProperties.seed().admin();
        String email = UserAdminService.normalizeEmail(admin.email());

        // 복구 모드: 관리자 비밀번호 분실·잠금 시 한 번만 켜서 실행 (docs/operations.md "관리자 복구")
        if (admin.resetOnStart()) {
            userRepository.findByEmail(email).ifPresentOrElse(user -> {
                user.resetPassword(passwordEncoder.encode(admin.password()));
                user.updateProfile(user.getName(), Role.ADMIN);
                user.changeEnabled(true);
                log.warn("[복구] 관리자 계정을 초기화했습니다: {} — 로그인 후 비밀번호를 바꾸고, ADMIN_RESET_ON_START 를 꺼 주세요.", email);
            }, () -> log.warn("[복구] {} 계정이 없어 초기화하지 못했습니다.", email));
            return;
        }

        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (userRepository.existsByEmail(email)) {
            log.warn("시드 관리자 이메일({})이 다른 역할 계정으로 이미 존재해 생성하지 않습니다.", admin.email());
            return;
        }
        userRepository.save(AppUser.create(email, passwordEncoder.encode(admin.password()),
                admin.name(), Role.ADMIN));
        log.info("최초 관리자 계정을 생성했습니다: {} (첫 로그인 시 비밀번호 변경 필요)", admin.email());
    }
}
