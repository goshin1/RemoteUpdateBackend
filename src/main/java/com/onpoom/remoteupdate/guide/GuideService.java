package com.onpoom.remoteupdate.guide;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.onpoom.remoteupdate.auth.UserPrincipal;
import com.onpoom.remoteupdate.common.error.ApiException;
import com.onpoom.remoteupdate.common.error.ErrorCode;
import com.onpoom.remoteupdate.project.Project;
import com.onpoom.remoteupdate.project.ProjectService;
import com.onpoom.remoteupdate.storage.FileCategory;
import com.onpoom.remoteupdate.storage.FileStorage;
import com.onpoom.remoteupdate.storage.StoredFile;
import com.onpoom.remoteupdate.storage.UploadValidator;
import com.onpoom.remoteupdate.update.DownloadTarget;
import com.onpoom.remoteupdate.user.AppUser;
import com.onpoom.remoteupdate.user.AppUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 프로젝트별 초기 세팅 가이드.
 * 업데이트 파일과 달리 가이드 첨부 파일은 교체할 수 있다 (교체 시 이전 파일은 커밋 후 삭제).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuideService {

    private final SetupGuideRepository guideRepository;
    private final AppUserRepository userRepository;
    private final ProjectService projectService;
    private final UploadValidator uploadValidator;
    private final FileStorage fileStorage;

    public List<GuideResponse> list(Long projectId) {
        projectService.getProject(projectId);
        return guideRepository.findByProjectIdOrderByIdAsc(projectId).stream().map(GuideResponse::from).toList();
    }

    public GuideResponse findOne(Long guideId) {
        return GuideResponse.from(getGuide(guideId));
    }

    /** 가이드 등록. 첨부 파일은 선택 */
    @Transactional
    public GuideResponse create(Long projectId, GuideRequest request, MultipartFile file, UserPrincipal author) {
        Project project = projectService.getProject(projectId);
        AppUser user = userRepository.findById(author.id())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        SetupGuide guide = SetupGuide.create(project, request.title().trim(), request.content(), user);
        if (file != null && !file.isEmpty()) {
            StoredFile stored = store(file);
            guide.attachFile(stored.key(), stored.originalName(), stored.size());
        }
        return GuideResponse.from(guideRepository.save(guide));
    }

    @Transactional
    public GuideResponse update(Long guideId, GuideRequest request) {
        SetupGuide guide = getGuide(guideId);
        guide.update(request.title().trim(), request.content());
        return GuideResponse.from(guide);
    }

    /** 첨부 파일 추가 또는 교체 */
    @Transactional
    public GuideResponse replaceAttachment(Long guideId, MultipartFile file) {
        SetupGuide guide = getGuide(guideId);
        String oldKey = guide.getFileKey();
        StoredFile stored = store(file);
        guide.attachFile(stored.key(), stored.originalName(), stored.size());
        if (oldKey != null) {
            afterCommit(() -> fileStorage.delete(oldKey));
        }
        return GuideResponse.from(guide);
    }

    /** 첨부 파일 다운로드 (이력은 남기지 않음) */
    public DownloadTarget attachment(Long guideId) {
        SetupGuide guide = getGuide(guideId);
        if (!guide.hasAttachment()) {
            throw new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return new DownloadTarget(fileStorage.load(guide.getFileKey()), guide.getFileName(), guide.getFileSize(),
                null);
    }

    private SetupGuide getGuide(Long guideId) {
        return guideRepository.findById(guideId).orElseThrow(() -> new ApiException(ErrorCode.GUIDE_NOT_FOUND));
    }

    /** 파일 저장 + 롤백되면 저장한 파일 삭제 */
    private StoredFile store(MultipartFile file) {
        String fileName = uploadValidator.validate(file);
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStorage.store(FileCategory.GUIDES, fileName, in);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.FILE_STORAGE_ERROR);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    fileStorage.delete(stored.key());
                }
            }
        });
        return stored;
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
