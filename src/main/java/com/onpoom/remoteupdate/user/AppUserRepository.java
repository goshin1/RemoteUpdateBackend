package com.onpoom.remoteupdate.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 사용자 Repository.
 * <p>
 * Spring Data JPA 는 메서드 이름을 해석해 쿼리를 자동으로 만든다.
 * 예) findByEmail → SELECT * FROM app_user WHERE email = ?
 *     existsByRoleAndEnabledTrue → SELECT 1 ... WHERE role = ? AND enabled = true LIMIT 1
 * <p>
 * JpaSpecificationExecutor 를 함께 상속하면 조건을 조합하는 동적 검색(findAll(spec, pageable))을 쓸 수 있다.
 * (관리자 사용자 목록의 키워드·역할·사용 여부 필터)
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    /** 활성 관리자 수 (마지막 관리자를 비활성화·강등하지 못하게 확인할 때 사용) */
    long countByRoleAndEnabledTrue(Role role);

    /** 활성 사용자 전체 (이름순) — 다운로드 이력 화면의 "다운로더" 선택 목록 */
    List<AppUser> findByEnabledTrueOrderByNameAsc();
}
