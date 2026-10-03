package com.onpoom.remoteupdate.config.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import com.onpoom.remoteupdate.config.AppProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 운영 배포용: 빌드된 Vue 화면(dist 폴더)을 이 서버가 함께 제공한다.
 *
 * <h3>왜 필요한가</h3>
 * 개발 중에는 Vite 개발 서버(5173)가 화면을, Spring(8080)이 API 를 제공하고 Vite 가 /api 를 대신 전달했다.
 * 운영에서는 서버 하나(8080)가 둘 다 제공하면 실행·배포가 단순하고, 화면과 API 가 같은 출처라 쿠키·CSRF 가 그대로 동작한다.
 *
 * <h3>SPA 새로고침 문제</h3>
 * Vue 라우터의 주소(/projects/3)는 실제 파일이 아니다. 브라우저에서 그 주소로 새로고침하면 서버에 /projects/3 파일을 요청하므로
 * 없는 파일이면 index.html 을 대신 돌려줘야 한다 (그러면 Vue 라우터가 화면을 그림). 이것을 "SPA fallback" 이라고 한다.
 * 단, /api 로 시작하는 주소는 여기서 처리하지 않는다 (없는 API 는 404 JSON 이어야 함).
 *
 * <h3>캐시</h3>
 * - /assets/* : 파일명에 내용 해시가 들어 있어(index-DT3FnbJp.js) 내용이 바뀌면 이름도 바뀜 → 1년 캐시
 * - index.html : 새 배포가 바로 보이도록 캐시하지 않음
 *
 * WebMvcConfigurer: 스프링 MVC 의 기본 설정에 우리 설정을 덧붙이는 인터페이스
 */
@Slf4j
@Configuration
public class FrontendConfig implements WebMvcConfigurer {

    private final Path distDir;

    public FrontendConfig(AppProperties appProperties) {
        String dir = appProperties.frontend() == null ? null : appProperties.frontend().distDir();
        this.distDir = dir == null || dir.isBlank() ? null : Path.of(dir).toAbsolutePath().normalize();
        if (distDir != null) {
            if (Files.isRegularFile(distDir.resolve("index.html"))) {
                log.info("Frontend 제공: {}", distDir);
            } else {
                log.warn("Frontend 폴더에 index.html 이 없습니다: {} (npm run build 를 먼저 실행하세요)", distDir);
            }
        }
    }

    /**
     * 루트 주소("/")는 정적 파일 처리기가 "빈 경로"로 보고 처리하지 않으므로 index.html 로 내부 전달(forward)한다.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (distDir != null) {
            registry.addViewController("/").setViewName("forward:/index.html");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (distDir == null) {
            return;
        }
        String location = distDir.toUri().toString(); // "file:///C:/.../dist/"

        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location + "assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());

        registry.addResourceHandler("/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new SpaFallbackResolver(distDir.resolve("index.html")));
    }

    /** 파일이 있으면 그 파일, 없으면 index.html (단, /api 경로는 제외) */
    static class SpaFallbackResolver extends PathResourceResolver {

        private final Resource index;

        SpaFallbackResolver(Path indexHtml) {
            this.index = new FileSystemResource(indexHtml);
        }

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = super.getResource(resourcePath, location); // 경로 조작(../) 검사 포함
            if (requested != null) {
                return requested;
            }
            if (resourcePath.startsWith("api/")) {
                return null; // 없는 API → 스프링의 404 처리로
            }
            return index.exists() ? index : null;
        }
    }
}
