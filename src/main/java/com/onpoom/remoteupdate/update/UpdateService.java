package com.onpoom.remoteupdate.update;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.onpoom.remoteupdate.auth.UserPrincipal;
import com.onpoom.remoteupdate.common.PageResponse;
import com.onpoom.remoteupdate.common.error.ApiException;
import com.onpoom.remoteupdate.common.error.ErrorCode;
import com.onpoom.remoteupdate.project.Project;
import com.onpoom.remoteupdate.project.ProjectService;
import com.onpoom.remoteupdate.storage.FileCategory;
import com.onpoom.remoteupdate.storage.FileStorage;
import com.onpoom.remoteupdate.storage.StoredFile;
import com.onpoom.remoteupdate.storage.UploadValidator;
import com.onpoom.remoteupdate.user.AppUser;
import com.onpoom.remoteupdate.user.AppUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UpdateService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UpdateInfoRepository updateRepository;
    private final UpdateHistoryRepository historyRepository;
    private final AppUserRepository userRepository;
    private final ProjectService projectService;
    private final UploadValidator uploadValidator;
    private final FileStorage fileStorage;
    private final JsonMapper jsonMapper;

    /**
     * 업데이트 목록 (최신 등록순).
     * 직원(STAFF)은 ACTIVE 만 보이고, 개발자 이상은 status 로 필터하거나 전체를 본다.
     */
    public PageResponse<UpdateResponse> list(Long projectId, UpdateStatus status, int page, int size,
            UserPrincipal viewer) {
        projectService.getProject(projectId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        UpdateStatus effective = viewer.isDeveloperOrAbove() ? status : UpdateStatus.ACTIVE;
        var result = effective == null
                ? updateRepository.findByProjectId(projectId, pageable)
                : updateRepository.findByProjectIdAndStatus(projectId, effective, pageable);
        return PageResponse.of(result, UpdateResponse::from);
    }

    public UpdateResponse findOne(Long updateId, UserPrincipal viewer) {
        return UpdateResponse.from(getVisibleUpdate(updateId, viewer));
    }

    /**
     * 업데이트 등록: 파일 저장(체크섬 계산) → DB 저장 → 이력(CREATE) 기록.
     * DB 저장이 실패해 롤백되면 이미 저장한 파일을 지운다.
     */
    @Transactional
    public UpdateResponse create(Long projectId, UpdateCreateRequest request, MultipartFile file,
            UserPrincipal author) {
        Project project = projectService.getProject(projectId);
        String version = request.version().trim();
        if (updateRepository.existsByProjectIdAndVersion(projectId, version)) {
            throw new ApiException(ErrorCode.DUPLICATE_VERSION);
        }
        String fileName = uploadValidator.validate(file);
        AppUser developer = getUser(author.id());

        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStorage.store(FileCategory.UPDATES, fileName, in);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.FILE_STORAGE_ERROR);
        }
        deleteFileIfRolledBack(stored.key());

        UpdateInfo info;
        try {
            // save 는 INSERT 를 트랜잭션 끝까지 미룰 수 있다. saveAndFlush 는 즉시 DB 로 보내서
            // 유니크 제약 위반(같은 버전 동시 등록)을 이 try 안에서 잡을 수 있게 한다
            info = updateRepository.saveAndFlush(UpdateInfo.create(project, developer, version,
                    request.title().trim(), request.content(), stored.key(), stored.originalName(),
                    stored.size(), stored.sha256()));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 버전이 등록된 경우 (유니크 제약)
            throw new ApiException(ErrorCode.DUPLICATE_VERSION);
        }
        historyRepository.save(UpdateHistory.of(info, HistoryAction.CREATE, developer, null, toJson(info)));
        log.info("업데이트 등록: projectId={}, version={}, size={}, by userId={}", projectId, version,
                stored.size(), author.id());
        return UpdateResponse.from(info);
    }

    /** 제목·내용만 수정. 변경이 있을 때만 이력(UPDATE) 기록 */
    @Transactional
    public UpdateResponse updateMeta(Long updateId, UpdateMetaRequest request, UserPrincipal editor) {
        UpdateInfo info = getUpdate(updateId);
        String before = toJson(info);
        info.updateMeta(request.title().trim(), request.content());
        String after = toJson(info);
        if (!before.equals(after)) {
            historyRepository.save(UpdateHistory.of(info, HistoryAction.UPDATE, getUser(editor.id()), before, after));
        }
        return UpdateResponse.from(info);
    }

    /** 비활성화(다운로드 차단) / 활성화. 상태가 실제로 바뀔 때만 이력 기록 */
    @Transactional
    public UpdateResponse changeStatus(Long updateId, UpdateStatus status, UserPrincipal editor) {
        UpdateInfo info = getUpdate(updateId);
        if (info.getStatus() != status) {
            String before = toJson(info);
            info.changeStatus(status);
            HistoryAction action = status == UpdateStatus.DISABLED ? HistoryAction.DISABLE : HistoryAction.ENABLE;
            historyRepository.save(UpdateHistory.of(info, action, getUser(editor.id()), before, toJson(info)));
            log.info("업데이트 상태 변경: updateId={}, {} by userId={}", updateId, status, editor.id());
        }
        return UpdateResponse.from(info);
    }

    /** 변경 이력 (오래된 순) */
    public List<UpdateHistoryResponse> history(Long updateId) {
        getUpdate(updateId);
        return historyRepository.findByUpdateIdOrderByIdAsc(updateId).stream()
                .map(h -> new UpdateHistoryResponse(h.getId(), h.getAction(), h.getChangedBy().getId(),
                        h.getChangedBy().getName(), parse(h.getBeforeJson()), parse(h.getAfterJson()),
                        h.getChangedAt()))
                .toList();
    }

    /** 직원에게는 비활성 업데이트를 "없는 것"으로 보여준다 */
    UpdateInfo getVisibleUpdate(Long updateId, UserPrincipal viewer) {
        UpdateInfo info = getUpdate(updateId);
        if (!viewer.isDeveloperOrAbove() && !info.isDownloadable()) {
            throw new ApiException(ErrorCode.UPDATE_NOT_FOUND);
        }
        return info;
    }

    private UpdateInfo getUpdate(Long updateId) {
        return updateRepository.findById(updateId).orElseThrow(() -> new ApiException(ErrorCode.UPDATE_NOT_FOUND));
    }

    private AppUser getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private String toJson(UpdateInfo info) {
        return jsonMapper.writeValueAsString(UpdateSnapshot.of(info));
    }

    private JsonNode parse(String json) {
        return json == null ? null : jsonMapper.readTree(json);
    }

    private void deleteFileIfRolledBack(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    log.warn("업데이트 등록 롤백으로 저장 파일 삭제: key={}", key);
                    fileStorage.delete(key);
                }
            }
        });
    }
}
