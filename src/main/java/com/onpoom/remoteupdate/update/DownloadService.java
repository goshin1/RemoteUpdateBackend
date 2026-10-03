package com.onpoom.remoteupdate.update;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onpoom.remoteupdate.auth.UserPrincipal;
import com.onpoom.remoteupdate.common.PageResponse;
import com.onpoom.remoteupdate.common.error.ApiException;
import com.onpoom.remoteupdate.common.error.ErrorCode;
import com.onpoom.remoteupdate.storage.FileStorage;
import com.onpoom.remoteupdate.user.AppUser;
import com.onpoom.remoteupdate.user.AppUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DownloadService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UpdateInfoRepository updateRepository;
    private final DownloadHistoryRepository downloadHistoryRepository;
    private final AppUserRepository userRepository;
    private final FileStorage fileStorage;

    /**
     * 다운로드 준비: 존재 확인 → 비활성이면 403 → 저장 파일 확인 → 이력 기록.
     * 파일 전송(스트리밍)은 트랜잭션이 끝난 뒤 컨트롤러에서 한다 (대용량 전송 동안 DB 연결을 잡고 있지 않도록).
     */
    @Transactional
    public DownloadTarget prepare(Long updateId, UserPrincipal downloader, String clientIp) {
        UpdateInfo info = updateRepository.findById(updateId)
                .orElseThrow(() -> new ApiException(ErrorCode.UPDATE_NOT_FOUND));
        if (!info.isDownloadable()) {
            throw new ApiException(ErrorCode.UPDATE_DISABLED);
        }
        Resource resource = fileStorage.load(info.getFileKey());
        AppUser user = userRepository.findById(downloader.id())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        downloadHistoryRepository.save(DownloadHistory.of(info, user, clientIp));
        log.info("다운로드: updateId={}, version={}, userId={}, ip={}", updateId, info.getVersion(), user.getId(),
                clientIp);
        return new DownloadTarget(resource, info.getFileName(), info.getFileSize(), info.getChecksum());
    }

    /** 다운로드 이력 검색 (최신순). 날짜는 하루 단위, to 날짜 포함 */
    public PageResponse<DownloadHistoryResponse> search(Long projectId, Long updateId, Long userId,
            LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE);
        }
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
        Specification<DownloadHistory> spec = Specification.allOf(
                DownloadHistorySpecs.projectId(projectId),
                DownloadHistorySpecs.updateId(updateId),
                DownloadHistorySpecs.userId(userId),
                DownloadHistorySpecs.downloadedBetween(fromTime, toExclusive));
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("downloadedAt"), Sort.Order.desc("id")));
        return PageResponse.of(downloadHistoryRepository.findAll(spec, pageable), DownloadHistoryResponse::from);
    }
}
