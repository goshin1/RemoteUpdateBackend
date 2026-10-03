package com.onpoom.remoteupdate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// 로그인은 AuthController/AuthService 가 직접 처리하므로 Boot 기본 인메모리 사용자(임시 비밀번호) 생성을 끔
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class RemoteUpdateBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(RemoteUpdateBackendApplication.class, args);
	}

}
