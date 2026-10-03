package com.onpoom.remoteupdate.update;

import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.Specification;

/** 다운로드 이력 검색 조건. null 인 조건은 무시된다 */
final class DownloadHistorySpecs {

    private DownloadHistorySpecs() {
    }

    static Specification<DownloadHistory> projectId(Long projectId) {
        return (root, query, cb) -> projectId == null ? null
                : cb.equal(root.get("update").get("project").get("id"), projectId);
    }

    static Specification<DownloadHistory> updateId(Long updateId) {
        return (root, query, cb) -> updateId == null ? null : cb.equal(root.get("update").get("id"), updateId);
    }

    static Specification<DownloadHistory> userId(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("user").get("id"), userId);
    }

    /** from 이상, to 미만 */
    static Specification<DownloadHistory> downloadedBetween(LocalDateTime from, LocalDateTime toExclusive) {
        return (root, query, cb) -> {
            if (from == null && toExclusive == null) {
                return null;
            }
            if (from == null) {
                return cb.lessThan(root.get("downloadedAt"), toExclusive);
            }
            if (toExclusive == null) {
                return cb.greaterThanOrEqualTo(root.get("downloadedAt"), from);
            }
            return cb.and(cb.greaterThanOrEqualTo(root.get("downloadedAt"), from),
                    cb.lessThan(root.get("downloadedAt"), toExclusive));
        };
    }
}
