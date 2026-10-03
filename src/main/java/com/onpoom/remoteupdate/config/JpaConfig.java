package com.onpoom.remoteupdate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * created_at / updated_at 자동 기록.
 * 메인 클래스가 아닌 별도 설정으로 두어 @WebMvcTest 같은 슬라이스 테스트에 영향을 주지 않게 함.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
