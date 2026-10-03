package com.onpoom.remoteupdate.auth;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onpoom.remoteupdate.common.error.ApiException;
import com.onpoom.remoteupdate.common.error.ErrorCode;
import com.onpoom.remoteupdate.config.AppProperties;
import com.onpoom.remoteupdate.user.AppUser;
import com.onpoom.remoteupdate.user.AppUserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    /** 존재하지 않는 이메일일 때도 BCrypt 비교를 수행해 응답 시간으로 계정 존재 여부가 드러나지 않게 함 */
    private final String dummyHash;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * 이메일/비밀번호 확인. 실패 횟수 기록은 예외가 나도 커밋되어야 하므로 noRollbackFor 지정.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public UserPrincipal authenticate(LoginRequest request) {
        LocalDateTime now = LocalDateTime.now();
        AppUser user = userRepository.findByEmail(request.email().trim()).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        // 잠긴 계정은 비밀번호를 확인하지 않음 (무차별 대입 방지)
        if (user.isLocked(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            AppProperties.Login policy = appProperties.security().login();
            user.recordLoginFailure(policy.maxFailures(), policy.lockDuration(), now);
            log.info("로그인 실패: userId={}, 연속 실패={}", user.getId(), user.getFailedLoginCount());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        // 비활성 계정 여부는 비밀번호가 맞을 때만 알려줌
        if (!user.isEnabled()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        user.recordLoginSuccess();
        return UserPrincipal.from(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        return MeResponse.from(findActiveUser(userId));
    }

    /** 본인 비밀번호 변경. 변경 후 세션의 사용자 정보를 갱신할 수 있도록 새 Principal 반환 */
    @Transactional
    public UserPrincipal changePassword(Long userId, PasswordChangeRequest request) {
        AppUser user = findActiveUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ApiException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        return UserPrincipal.from(user);
    }

    private AppUser findActiveUser(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (!user.isEnabled()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }
}
