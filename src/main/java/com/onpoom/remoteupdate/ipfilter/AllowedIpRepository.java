package com.onpoom.remoteupdate.ipfilter;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AllowedIpRepository extends JpaRepository<AllowedIp, Long> {

    /** IP 제한 판단용: 사용 중인 항목만 */
    List<AllowedIp> findByEnabledTrue();

    /** 관리 화면 목록 (등록자 이름을 함께 조회) */
    @EntityGraph(attributePaths = "createdBy")
    List<AllowedIp> findAllByOrderByIdAsc();

    /**
     * 같은 값이 이미 있는지.
     * <p>
     * 주의: 메서드 이름을 existsByIpOrCidr 로 지으면 Spring Data 가 "Or" 를 키워드로 해석해
     * "ip = ? OR cidr = ?" 쿼리를 만들려다 "No property 'ip' found" 오류로 앱이 뜨지 않는다.
     * 필드 이름(ipOrCidr)에 Or/And 같은 키워드가 들어 있으면 이렇게 @Query 로 직접 쿼리를 적는다.
     * (JPQL: 테이블·컬럼이 아니라 엔티티·필드 이름으로 쓰는 쿼리)
     */
    @Query("select count(a) > 0 from AllowedIp a where a.ipOrCidr = :value")
    boolean existsByValue(@Param("value") String value);
}
