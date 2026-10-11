# 7부. 테스트

[← 6부](06-files-io.md) · [목차](../backend-concepts.md) · [8부 →](08-operations.md)

---

## 34장. 왜 테스트를 쓰나 — 테스트 피라미드

### 이야기

Phase 5 에서 사용자 관리 기능을 추가하다가 세션 필터를 고쳤습니다. 이 수정이 Phase 2 의 로그인, Phase 3 의 업로드를 망가뜨리지 않았는지 어떻게 알까요? 매번 화면을 열어 처음부터 다 눌러 볼 수는 없습니다.
**테스트 코드**는 "이렇게 하면 이렇게 돼야 한다" 를 코드로 적어 두고, 명령 하나로 **전부 다시 확인**하게 해 줍니다. 우리 Backend 에는 테스트가 50개 넘게 있고 `.\gradlew.bat test` 한 번에 모두 돌아갑니다.

### 개념: 세 종류의 테스트

```
            ▲  느림·비쌈·실제와 가까움
           ╱ ╲
          ╱E2E╲          실제 브라우저 → 프런트 → 백엔드 → DB   (Playwright, 36장)
         ╱─────╲
        ╱ 통합  ╲        스프링 전체 + DB, HTTP 는 흉내         (MockMvc, 35장)
       ╱─────────╲
      ╱   단위    ╲      클래스 하나, 스프링 없이               (ClientIpResolverTest)
     ╱─────────────╲
            ▼  빠름·쌈·문제 위치가 정확함
```

| 종류 | 범위 | 장점 | 단점 | 우리 예 |
|---|---|---|---|---|
| 단위 | 클래스 하나 | 밀리초, 실패 원인이 명확 | 연결 부분의 문제는 못 찾음 | `ClientIpResolverTest` |
| 통합 | 컨트롤러~DB 전체 | 실제 동작과 가까움, 보안 필터까지 통과 | 수 초 | `UpdateApiTest`, `AuthApiTest` 등 |
| E2E | 브라우저까지 | 사용자가 겪는 그대로 | 느리고 깨지기 쉬움 | Phase 4·6·7 Playwright |

아래로 갈수록 많이, 위로 갈수록 적게 — 이것을 **테스트 피라미드**라고 합니다. 우리는 API 서버라 통합 테스트가 가장 많습니다.

---

## 35장. 통합 테스트 — @SpringBootTest 와 MockMvc

### 도구

| 도구 | 역할 |
|---|---|
| `@SpringBootTest` | 실제 앱과 같은 스프링 컨테이너(모든 빈)를 띄움 |
| `@AutoConfigureMockMvc` + `MockMvc` | 서버를 진짜로 띄우지 않고 HTTP 요청을 **흉내 내서** 필터~컨트롤러~DB 까지 전부 통과시킴 |
| H2 (MariaDB 모드) | 메모리 DB. MariaDB 설치 없이 `gradlew test` 가 돌아감 |
| `@ActiveProfiles("test")` | `application-test.yml` 적용 (DB 를 H2 로, 3부 14장) |
| `spring-security-test` 의 `csrf()` | 테스트 요청에 올바른 CSRF 토큰을 붙여 줌 |
| AssertJ `assertThat` | 결과 확인 (`assertThat(x).isEqualTo(y)`) |

### 실제 테스트 읽기

```java
@Test
void 같은_버전은_등록할_수_없다() throws Exception {
    register("2.0.0", "a.zip").andExpect(status().isCreated());       // 첫 등록은 201
    register("2.0.0", "b.zip")
            .andExpect(status().isConflict())                          // 두 번째는 409
            .andExpect(jsonPath("$.code").value("DUPLICATE_VERSION")); // 오류 코드까지 확인
}
```

`jsonPath("$.code")` 는 응답 JSON 의 `code` 필드를 꺼냅니다. 응답만 보는 게 아니라 **DB 와 디스크까지** 확인하는 테스트도 있습니다.

```java
@Test
void 개발자가_업데이트를_등록하면_체크섬과_이력이_저장된다() throws Exception {
    String body = register("1.0.0", "module-1.0.0.zip")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.checksum").value(sha256(FILE_BYTES)))
            .andExpect(jsonPath("$.fileKey").doesNotExist())       // 숨겨야 할 값이 응답에 없는지 (3부 17장)
            .andReturn().getResponse().getContentAsString();
    ...
    assertThat(Files.readAllBytes(stored)).isEqualTo(FILE_BYTES);  // 디스크에 같은 내용으로 저장됐는지
    assertThat(saved.getFileKey()).startsWith("updates/").doesNotContain("module");  // 저장 이름은 서버가 (6부 31장)
    assertThat(history).extracting(UpdateHistory::getAction).containsExactly(HistoryAction.CREATE);
}
```

### 공통 준비는 부모 클래스로

사용자 만들기와 로그인은 거의 모든 테스트에 필요합니다. `ApiTestSupport` 에 두고 상속합니다.

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {
    protected AppUser createUser(Role role) { ... }                       // 역할별 사용자 생성
    protected MockHttpSession loginAs(Role role) throws Exception { ... } // 생성 + 실제 로그인 API 호출 → 세션
}
// 사용: mvc.perform(post(...).session(admin).with(csrf()) ...)
```

### 컨텍스트 캐싱과 테스트 격리

스프링 컨테이너를 띄우는 데는 몇 초가 걸립니다. 설정이 같은 테스트 클래스들은 컨테이너를 **한 번만 띄워 재사용**합니다 (컨텍스트 캐싱). 빠르지만 **DB 도 공유**되므로, 테스트끼리 데이터가 겹치면 실행 순서에 따라 결과가 달라집니다.
- 해결: 테스트마다 이메일·프로젝트 이름에 `UUID` 를 붙여 서로 겹치지 않게 했습니다 (`"업데이트테스트-" + UUID.randomUUID()`).
- `@DirtiesContext` 를 붙이면 그 테스트는 새 컨테이너를 씁니다. 느리므로 꼭 필요할 때만 (`CsrfCookieTest` — `csrf()` 도우미가 CSRF 저장소를 바꿔 끼우는 것을 피하고 진짜 쿠키 방식을 확인하려고).

> 실행: `.\gradlew.bat test` → 결과 보고서 `build/reports/tests/test/index.html`

---

## 36장. E2E 테스트 — 브라우저로 끝까지

MockMvc 는 빠르고 정확하지만 **브라우저가 실제로 쿠키·리다이렉트·파일 저장을 어떻게 하는지**는 확인하지 못합니다. 그래서 큰 단계마다 백엔드와 화면을 띄우고 **Playwright**(브라우저 자동화 도구)로 실제 Chromium 을 조작해 확인했습니다.

| 확인한 것 | 왜 E2E 로만 알 수 있나 |
|---|---|
| 로그인 → 비밀번호 변경 강제 → 변경 후 진입 | 프런트 라우터 + 서버 응답의 조합 |
| CSRF 쿠키 자동 발급 → 헤더 첨부 | 브라우저 쿠키 동작 |
| 다운로드 파일 내용·한글 이름·SHA-256 이 화면 값과 같은지 | 브라우저의 `Content-Disposition` 해석 |
| 가이드에 넣은 `<script>` 가 실행되지 않는지 | 실제 렌더링 (5부 29장) |
| 세션 쿠키를 지운 뒤 이동 → 로그인 화면 → 원래 화면으로 복귀 | 세션 만료 흐름 |
| 다른 IP 에서 접속 → 안내줄·등록 차단 | IP 제한 전체 흐름 |
| 휴대폰 폭에서 가로 스크롤이 생기지 않는지 | 화면 배치 |

**여기서만 발견된 문제들**
- 표 안의 숨김 텍스트(`position: absolute`) 때문에 휴대폰 화면이 가로로 넘침
- 업로드 정책을 받기 전에 파일을 고르면 확장자 검사를 건너뜀 (2부 10장 경쟁 조건)
- 운영 방식으로 띄우면 화면 파일까지 비밀번호 변경 필터에 막힘 (8부 40장)

> 사용 설명서(Frontend `docs/user-manual.md`)의 화면 그림도 같은 방식으로, 예시 데이터를 실제로 등록하며 찍었습니다.

---

## 37장. 좋은 테스트를 쓰는 습관

| 습관 | 설명 | 우리 예 |
|---|---|---|
| **이름은 문장으로** | 실패했을 때 이름만 보고 무엇이 깨졌는지 알게 | `비활성화하면_이미_로그인한_세션도_즉시_끊긴다()` |
| **준비 → 실행 → 확인** (Given-When-Then) | 세 덩어리로 나눠 읽기 쉽게 | `setUp()` 에서 준비, `register()` 실행, `andExpect` 확인 |
| **실패하는 경우를 더 많이** | 성공 1개당 거부·경계 상황 여러 개 | 같은 버전, 빈 파일, 허용 안 된 확장자, 경로가 붙은 파일 이름, 직원의 등록 시도 |
| **서로 독립** | 순서·다른 테스트에 기대지 않음 | UUID 로 데이터 분리 |
| **버그를 고치면 테스트부터** | 같은 버그가 다시 생기면 바로 실패하게 (회귀 테스트) | 운영 모드 버그 → `FrontendServingTest.임시_비밀번호_상태에서도_화면_파일은_받을_수_있다()` |
| **보안은 "안 되는 것" 을 테스트** | 되는 것보다 막히는 것을 확인 | `IpRestrictionTest`, 응답에 `fileKey` 가 없는지 |
| **시간·랜덤은 바깥에서 넣기** | `LocalDateTime.now()` 를 메서드 안에서 부르면 테스트가 어려움 | `recordLoginFailure(..., now)` 처럼 파라미터로 (4부 24장) |

### 정리

- 단위 → 통합 → E2E 로 갈수록 느리지만 실제에 가깝다. 아래를 넓게.
- MockMvc 통합 테스트는 필터·검증·DB·디스크까지 한 번에 확인한다.
- 버그를 고치면 그 버그를 재현하는 테스트를 먼저 남긴다.

### 확인 문제

37-1. "직원은 업데이트를 등록할 수 없다" 를 테스트한다면, 어떤 요청을 보내고 무엇을 확인해야 할까요? 응답 코드 말고 하나 더 확인할 만한 것은?

---

[← 6부](06-files-io.md) · [목차](../backend-concepts.md) · [8부. 운영 →](08-operations.md)
