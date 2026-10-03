package com.onpoom.remoteupdate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * 애플리케이션 시작점.
 * <ul>
 *   <li>@SpringBootApplication = 세 가지를 합친 것
 *     <ul>
 *       <li>@Configuration: 이 클래스도 설정 클래스</li>
 *       <li>@EnableAutoConfiguration: 의존성(라이브러리)을 보고 DB 연결, 웹 서버, JPA 등을 자동 설정</li>
 *       <li>@ComponentScan: 이 패키지(com.onpoom.remoteupdate) 아래의 @Component/@Service/@Controller 등을 찾아 빈으로 등록
 *           → 그래서 모든 클래스는 이 패키지 아래에 있어야 한다</li>
 *     </ul>
 *   </li>
 *   <li>exclude = UserDetailsServiceAutoConfiguration: 로그인은 AuthController/AuthService 가 직접 처리하므로
 *       Boot 기본 인메모리 사용자(로그에 임시 비밀번호를 찍는 기능) 생성을 끔</li>
 *   <li>@ConfigurationPropertiesScan: AppProperties 처럼 @ConfigurationProperties 가 붙은 클래스를 찾아 설정값을 채움</li>
 * </ul>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class RemoteUpdateBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(RemoteUpdateBackendApplication.class, args);
	}

}
