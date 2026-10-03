package com.onpoom.remoteupdate.user;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onpoom.remoteupdate.auth.UserPrincipal;
import com.onpoom.remoteupdate.common.PageResponse;
import com.onpoom.remoteupdate.common.error.ApiException;
import com.onpoom.remoteupdate.common.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자의 사용자 관리 (기획서 2.2 계정 운영 정책).
 * <ul>
 *   <li>회원가입은 없고 관리자가 계정을 발급한다 → 서버가 임시 비밀번호를 만들어 한 번만 보여준다.</li>
 *   <li>계정은 삭제하지 않고 비활성화한다 → 업데이트·다운로드 이력의 "누가"가 사라지지 않게.</li>
 *   <li>관리자가 스스로를 강등·비활성화하거나, 마지막 관리자가 사라지는 변경은 막는다 → 아무도 관리할 수 없는 상태 방지.</li>
 * </ul>
 * <p>
 * 비활성화·역할 변경은 SessionUserRefreshFilter 가 다음 요청에서 바로 반영한다 (이미 로그인한 세션 포함).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 클래스 기본은 읽기 전용, 데이터를 바꾸는 메서드에만 @Transactional 을 다시 붙인다
public class UserAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;

    public PageResponse<UserResponse> search(String keyword, Role role, Boolean enabled, int page, int size) {
        var spec = Specification.allOf(UserSpecs.keyword(keyword), UserSpecs.role(role), UserSpecs.enabled(enabled));
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("name").and(Sort.by("id")));
        return PageResponse.of(userRepository.findAll(spec, pageable), UserResponse::from);
    }

    /** 다운로드 이력 필터용 활성 사용자 목록 */
    public List<UserOptionResponse> options() {
        return userRepository.findByEnabledTrueOrderByNameAsc().stream().map(UserOptionResponse::from).toList();
    }

    /** 사용자 등록. 임시 비밀번호를 만들어 해시로 저장하고, 원문은 응답으로 한 번만 돌려준다. */
    @Transactional
    public TemporaryPasswordResponse create(UserCreateRequest request) {
        // 이메일은 소문자로 통일 (Admin@x.com 과 admin@x.com 을 같은 계정으로 취급)
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }
        String temporaryPassword = passwordGenerator.generate();
        AppUser user = userRepository.save(
                AppUser.create(email, passwordEncoder.encode(temporaryPassword), request.name().trim(), request.role()));
        log.info("사용자 등록: userId={}, role={}", user.getId(), user.getRole());
        return new TemporaryPasswordResponse(UserResponse.from(user), temporaryPassword);
    }

    @Transactional
    public UserResponse update(Long userId, UserUpdateRequest request, UserPrincipal admin) {
        AppUser user = getUser(userId);
        if (user.getRole() != request.role()) {
            // 역할 변경은 본인 불가 + 마지막 관리자 강등 불가
            ensureNotSelf(userId, admin);
            if (user.getRole() == Role.ADMIN && user.isEnabled()) {
                ensureAnotherActiveAdmin();
            }
        }
        user.updateProfile(request.name().trim(), request.role());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse changeEnabled(Long userId, boolean enabled, UserPrincipal admin) {
        AppUser user = getUser(userId);
        if (user.isEnabled() == enabled) {
            return UserResponse.from(user);
        }
        ensureNotSelf(userId, admin);
        if (!enabled && user.getRole() == Role.ADMIN) {
            ensureAnotherActiveAdmin();
        }
        user.changeEnabled(enabled);
        log.info("사용자 {}: userId={}", enabled ? "활성화" : "비활성화", userId);
        return UserResponse.from(user);
    }

    /** 비밀번호 초기화: 새 임시 비밀번호 발급 + 로그인 잠금 해제 + 첫 로그인 변경 강제 */
    @Transactional
    public TemporaryPasswordResponse resetPassword(Long userId) {
        AppUser user = getUser(userId);
        String temporaryPassword = passwordGenerator.generate();
        user.resetPassword(passwordEncoder.encode(temporaryPassword));
        log.info("비밀번호 초기화: userId={}", userId);
        return new TemporaryPasswordResponse(UserResponse.from(user), temporaryPassword);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AppUser getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private static void ensureNotSelf(Long userId, UserPrincipal admin) {
        if (userId.equals(admin.id())) {
            throw new ApiException(ErrorCode.CANNOT_CHANGE_OWN_ACCOUNT);
        }
    }

    /** 지금 바꾸려는 관리자 말고도 활성 관리자가 남는지 */
    private void ensureAnotherActiveAdmin() {
        if (userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ApiException(ErrorCode.LAST_ADMIN);
        }
    }
}
