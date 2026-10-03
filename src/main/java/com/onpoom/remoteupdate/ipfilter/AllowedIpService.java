package com.onpoom.remoteupdate.ipfilter;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onpoom.remoteupdate.auth.UserPrincipal;
import com.onpoom.remoteupdate.common.error.ApiException;
import com.onpoom.remoteupdate.common.error.ErrorCode;
import com.onpoom.remoteupdate.user.AppUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 허용 IP 관리.
 * <p>
 * 허용 IP 는 이력이 아니라 "설정"이므로 삭제를 허용한다 (기획서 7장).
 * 단, 지금 접속한 관리자 자신의 IP 를 막게 되는 변경(끄기·삭제)은 거부한다 → 스스로 잠기는 사고 방지.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllowedIpService {

    private final AllowedIpRepository allowedIpRepository;
    private final AppUserRepository userRepository;
    private final IpAccessPolicy policy;

    public AllowedIpOverview overview(String clientIp) {
        List<AllowedIpResponse> items = allowedIpRepository.findAllByOrderByIdAsc().stream()
                .map(AllowedIpResponse::from).toList();
        return new AllowedIpOverview(policy.isEnabled(), policy.isAlwaysAllowLocalhost(), clientIp,
                policy.isManagementAllowed(clientIp), items);
    }

    @Transactional
    public AllowedIpResponse add(AllowedIpRequest request, UserPrincipal admin) {
        String value = request.ipOrCidr().trim();
        if (!IpAccessPolicy.isValidIpOrCidr(value)) {
            throw new ApiException(ErrorCode.INVALID_IP);
        }
        if (allowedIpRepository.existsByValue(value)) {
            throw new ApiException(ErrorCode.DUPLICATE_IP);
        }
        try {
            AllowedIp saved = allowedIpRepository.saveAndFlush(AllowedIp.create(value,
                    request.description() == null ? null : request.description().trim(),
                    userRepository.getReferenceById(admin.id())));
            log.info("허용 IP 추가: {} by userId={}", value, admin.id());
            return AllowedIpResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.DUPLICATE_IP);
        }
    }

    @Transactional
    public AllowedIpResponse changeEnabled(Long id, boolean enabled, String clientIp, UserPrincipal admin) {
        AllowedIp target = get(id);
        if (target.isEnabled() && !enabled) {
            ensureStillAllowedWithout(id, clientIp);
        }
        target.changeEnabled(enabled);
        log.info("허용 IP {}: {} by userId={}", enabled ? "사용" : "중지", target.getIpOrCidr(), admin.id());
        return AllowedIpResponse.from(target);
    }

    @Transactional
    public void delete(Long id, String clientIp, UserPrincipal admin) {
        AllowedIp target = get(id);
        if (target.isEnabled()) {
            ensureStillAllowedWithout(id, clientIp);
        }
        allowedIpRepository.delete(target);
        log.info("허용 IP 삭제: {} by userId={}", target.getIpOrCidr(), admin.id());
    }

    /** id 항목이 빠진 뒤에도 지금 IP 가 허용되는지 미리 계산 */
    private void ensureStillAllowedWithout(Long id, String clientIp) {
        if (!policy.isEnabled()) {
            return;
        }
        List<String> remaining = allowedIpRepository.findByEnabledTrue().stream()
                .filter(ip -> !ip.getId().equals(id))
                .map(AllowedIp::getIpOrCidr)
                .toList();
        if (!policy.isAllowedBy(clientIp, remaining)) {
            throw new ApiException(ErrorCode.CANNOT_LOCK_OUT_SELF);
        }
    }

    private AllowedIp get(Long id) {
        return allowedIpRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.ALLOWED_IP_NOT_FOUND));
    }
}
