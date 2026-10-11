# 3부. 스프링 핵심

[← 2부](02-java-basics.md) · [목차](../backend-concepts.md) · [4부 →](04-database-jpa.md)

1부에서 "HTTP 요청을 받아 응답을 돌려준다" 가 백엔드의 일이라고 했습니다. 순수 자바로 이걸 하려면 소켓을 열고, HTTP 글자를 해석하고, 스레드를 관리하고 ... 할 일이 끝이 없습니다.
**스프링**은 이런 반복 작업을 대신 해 주고, 우리는 **업무 규칙**만 쓰게 해 주는 프레임워크입니다. 이 부에서는 스프링이 "무엇을, 어떻게" 대신 해 주는지 봅니다.

---

## 11장. 스프링 부트가 대신 해 주는 일

### 이야기

우리 앱의 시작점은 이게 전부입니다.

```java
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class RemoteUpdateBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(RemoteUpdateBackendApplication.class, args);
    }
}
```

이 몇 줄로 웹 서버가 8080 포트에서 뜨고, DB 에 연결되고, 보안 필터가 걸립니다. 어떻게 가능할까요?

### 개념: 스프링 부트의 세 가지 선물

| 선물 | 설명 | 우리 프로젝트 |
|---|---|---|
| **스타터(starter)** | 함께 써야 하는 라이브러리 묶음을 한 줄로 | `build.gradle` 의 `spring-boot-starter-webmvc`(웹+톰캣+Jackson), `-data-jpa`(JPA+Hibernate+커넥션 풀), `-security`, `-validation` |
| **자동 설정(auto-configuration)** | "이 라이브러리가 있고, 이 설정이 있으면 이 객체를 만들어 둔다" | `spring.datasource.url` 이 있으니 DB 연결 객체 생성, Jackson 이 있으니 JSON 변환기 등록 |
| **내장 웹 서버** | 톰캣을 따로 설치하지 않고 jar 안에 포함 | `java -jar RemoteUpdateBackend.jar` 하나로 실행 (8부 38장) |

`@SpringBootApplication` 은 세 가지 일을 합니다.
1. 이 클래스가 있는 패키지(`com.onpoom.remoteupdate`) **아래를 전부 훑어** `@Component` 류를 찾습니다 (**컴포넌트 스캔**). 그래서 모든 클래스는 이 패키지 아래에 있어야 합니다.
2. 자동 설정을 켭니다.
3. 이 클래스 자체도 설정 클래스로 씁니다.

**자동 설정 끄기**: 자동 설정이 원치 않는 것을 만들 때는 뺄 수 있습니다. 우리는 `UserDetailsServiceAutoConfiguration` 을 뺐습니다. 스프링 시큐리티가 기본으로 만드는 "임시 사용자(user/랜덤 비밀번호)" 가 필요 없기 때문입니다 (로그인은 우리 `AuthService` 가 직접 함).

> 원칙: **관례를 따르면 설정이 거의 없고, 다르게 하고 싶은 것만 설정한다** (Convention over Configuration).

### 정리

- 스타터 = 라이브러리 묶음, 자동 설정 = 있으면 알아서 만들어 둠, 내장 서버 = jar 하나로 실행.
- `@SpringBootApplication` 이 있는 패키지 아래만 스캔한다.

### 확인 문제

11-1. 새 클래스를 `com.onpoom.tools` 패키지에 `@Service` 로 만들었는데 주입이 안 됩니다. 왜일까요?

---

## 12장. 빈과 의존성 주입(DI)

### 이야기

`UpdateService` 는 Repository, FileStorage, ProjectService ... 여러 객체가 필요합니다. 직접 만든다면:

```java
public class UpdateService {
    private final FileStorage fileStorage = new LocalFileStorage(new AppProperties(...));   // 설정값은 어디서?
    private final ProjectService projectService = new ProjectService(new ProjectRepository...); // 인터페이스는 new 불가
}
```

만드는 순서, 설정값, 같은 객체를 여러 곳에서 공유하는 문제가 금방 엉킵니다.

### 개념: 객체는 스프링이 만들고, 우리는 받기만 한다

`@Service`, `@Repository`, `@Component`, `@Controller` 가 붙은 클래스는 스프링이 앱 시작 때 **객체를 하나만 만들어서 보관**합니다. 이 객체를 **빈(Bean)**, 보관하는 곳을 **스프링 컨테이너(ApplicationContext)** 라고 부릅니다.
다른 클래스가 그 빈을 필요로 하면, 직접 `new` 하지 않고 **생성자 파라미터로 받습니다**. 스프링이 알아서 넣어 줍니다. 이것이 **의존성 주입(DI, Dependency Injection)** 입니다.

```java
@Service
@RequiredArgsConstructor          // Lombok: final 필드를 받는 생성자를 자동 생성
public class UpdateService {
    private final UpdateInfoRepository updateRepository;   // 스프링이 주입
    private final FileStorage fileStorage;                 // 인터페이스 타입으로 받음 → LocalFileStorage 가 들어옴
}
```

객체를 "누가 만들지" 의 주도권이 내 코드에서 스프링으로 넘어갔다고 해서 **제어의 역전(IoC)** 이라고도 합니다.

### 왜 좋은가

- 클래스끼리 직접 `new` 로 묶이지 않아서, 구현을 바꾸거나(2부 6장) 테스트에서 가짜로 갈아끼우기 쉽습니다.
- 만드는 순서는 스프링이 알아서 정합니다 (A 가 B 를 필요로 하면 B 를 먼저).
- 빈은 기본적으로 **싱글톤**(앱 전체에 하나)이라 메모리를 아낍니다. 그 대신 **요청마다 바뀌는 값을 필드로 두면 안 됩니다** (2부 10장).

### 주입 방법 세 가지 중 생성자 주입을 쓰는 이유

| 방법 | 예 | 평가 |
|---|---|---|
| **생성자 주입** | `@RequiredArgsConstructor` + `final` 필드 | ✅ 불변(`final`), 빠진 의존성은 시작할 때 바로 오류, 테스트에서 `new` 로 직접 만들 수 있음 |
| 필드 주입 | `@Autowired private X x;` | 짧지만 `final` 불가, 테스트에서 넣기 어려움 |
| setter 주입 | `@Autowired void setX(X x)` | 선택적 의존성에만 |

생성자를 직접 쓴 예도 있습니다. `AuthService` 는 생성자 안에서 "가짜 비밀번호 해시" 를 미리 계산해야 해서 Lombok 대신 생성자를 손으로 썼습니다 (5부 28장).
`ClientIpResolverTest` 는 스프링 없이 `new ClientIpResolver(...)` 로 바로 만들어 테스트합니다 — 생성자 주입 덕분입니다.

### 내가 만들지 않은 클래스를 빈으로: @Bean

`BCryptPasswordEncoder` 는 라이브러리 클래스라 `@Component` 를 붙일 수 없습니다. 이럴 때 `@Configuration` 클래스 안에 **만드는 방법을 메서드로** 적습니다.

```java
@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();      // 이 반환값이 빈으로 등록됨
    }
}
```

이제 어디서든 `PasswordEncoder` 를 생성자로 받으면 이 객체가 들어옵니다.

### 정리

- 빈 = 스프링이 만들어 보관하는 객체(기본 하나), DI = 필요한 빈을 생성자로 받는 것.
- 생성자 주입 + `final` 이 기본.
- 라이브러리 클래스는 `@Configuration` + `@Bean` 으로 등록한다.

### 확인 문제

12-1. `FileStorage` 를 구현한 클래스가 `LocalFileStorage` 와 `S3FileStorage` 두 개 다 `@Component` 라면, `UpdateService` 를 만들 때 스프링은 어떤 문제를 겪을까요?

---

## 13장. 프록시와 AOP — 어노테이션이 동작하는 원리

### 이야기

2부 5장에서 "`@Transactional` 은 메모일 뿐, 읽는 쪽이 일한다" 고 했습니다. 그런데 `ProjectService.create()` 를 부르는 것은 컨트롤러인데, **트랜잭션 시작·커밋은 누가** 할까요?

### 개념: 스프링은 빈을 "대리인" 으로 감싼다

`@Transactional` 이나 `@PreAuthorize` 가 붙은 빈은 스프링이 **겉을 한 겹 감싼 대리 객체(프록시, proxy)** 를 만들어 대신 주입합니다.

```
컨트롤러 ──호출──▶ [ProjectService 프록시] ──▶ [진짜 ProjectService]
                   1. 트랜잭션 시작                create() 실행
                   2. 진짜 객체 호출 ────────────▶
                   3. 정상 종료면 커밋 ◀──────────
                      예외면 롤백
```

컨트롤러는 진짜 `ProjectService` 를 받았다고 생각하지만, 실제로 받은 것은 프록시입니다. `@PreAuthorize` 도 같은 원리로, 프록시가 **메서드 실행 전에 권한을 검사**합니다.

이렇게 여러 메서드에 공통으로 필요한 일(트랜잭션, 권한 검사, 로그 등)을 핵심 코드 밖에서 끼워 넣는 방식을 **AOP(관점 지향 프로그래밍)** 라고 합니다. 업무 코드에 `begin()`, `commit()`, `if (권한 없음)` 이 섞이지 않게 해 줍니다.

### 함정: 같은 클래스 안에서 부르면 프록시를 건너뛴다

```java
@Service
public class SomeService {
    public void outer() {
        this.inner();          // ❌ this 는 진짜 객체 → 프록시를 거치지 않음 → 트랜잭션 없음
    }
    @Transactional
    public void inner() { ... }
}
```

프록시는 **바깥에서 들어오는 호출**만 가로챌 수 있습니다. 같은 클래스 안의 호출은 진짜 객체끼리의 호출이라 `@Transactional`, `@PreAuthorize` 가 **조용히 무시**됩니다.
해결: 트랜잭션이 필요한 쪽을 **다른 빈으로 분리**하고 주입받아 호출합니다.

> 그 밖에 프록시가 동작하지 않는 경우: `private` 메서드, `final` 메서드/클래스.

### 정리

- `@Transactional`, `@PreAuthorize` 는 스프링이 만든 프록시가 메서드 앞뒤로 일을 끼워 넣어 동작한다 (AOP).
- 같은 클래스 안의 호출, private 메서드에는 적용되지 않는다.

### 확인 문제

13-1. 컨트롤러 메서드에 붙은 `@PreAuthorize("hasRole('ADMIN')")` 은 컨트롤러 메서드가 실행되기 전과 후 중 언제 검사될까요? 검사에 실패하면 서비스 코드는 실행될까요?

---

## 14장. 설정 값 다루기 — yml, 환경 변수, 프로필

### 이야기

DB 비밀번호, 파일 저장 위치, 로그인 잠금 횟수 ... 이런 값을 코드에 적어 두면 환경(개발 PC / 운영 서버)이 바뀔 때마다 **코드를 고쳐 다시 빌드**해야 합니다. 비밀번호가 Git 에 올라가는 문제도 생깁니다.
그래서 값은 **코드 밖**에 둡니다.

### 개념 1: application.yml

```yaml
server:
  port: 8080
app:
  security:
    login:
      max-failures: 5
      lock-duration: 15m
```

스프링 부트가 시작할 때 읽습니다. `spring.*`, `server.*` 는 스프링이 쓰는 설정, `app.*` 은 우리가 만든 설정입니다.

### 개념 2: 환경 변수로 덮어쓰기

```yaml
spring:
  datasource:
    password: ${DB_PASSWORD:}       # 환경 변수 DB_PASSWORD 가 있으면 그 값, 없으면 빈 값
app:
  storage:
    root: ${STORAGE_ROOT:./storage}  # 없으면 ./storage
```

`${이름:기본값}` 문법입니다. **같은 jar 를** 개발 PC 에서는 기본값으로, 운영 서버에서는 환경 변수로 다른 값을 주며 실행합니다. 비밀 값은 파일이 아니라 운영 서버의 환경 변수에만 둡니다 (8부 40장).

### 개념 3: 설정을 객체로 받기 — @ConfigurationProperties

`app.*` 값을 타입이 있는 record 로 받습니다.

```java
@ConfigurationProperties(prefix = "app")
public record AppProperties(Storage storage, Upload upload, Security security, Seed seed, Frontend frontend) {
    public record Login(int maxFailures, Duration lockDuration) { }
    ...
}
// 사용: appProperties.security().login().maxFailures()
```

- yml 의 `max-failures`(케밥) ↔ 자바 `maxFailures`(카멜)를 알아서 맞춥니다.
- `15m` → `Duration`, `zip, exe, msi` → `Set<String>` 같은 변환도 자동입니다.
- 이 클래스를 찾아 읽게 하는 것이 `@ConfigurationPropertiesScan` 입니다 (2부 확인 문제 5-1 의 답).

**@Value — 값 하나만 필요할 때**

```java
public UploadPolicyController(AppProperties appProperties,
        @Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) { ... }
```

스프링 기본 설정 하나를 읽을 때는 `@Value` 가 간편합니다. 우리 앱 설정처럼 **묶음**은 `@ConfigurationProperties` 가 낫습니다 — 한곳에 모이고, 오타를 찾기 쉽고, IDE 자동 완성이 됩니다.

### 개념 4: 프로필 — 상황별 설정 묶음

`application-test.yml` 은 **test 프로필이 켜졌을 때만** 추가로 읽혀서 기본 설정을 덮어씁니다.

```
application.yml            ← 항상 읽음 (기본: MariaDB)
application-test.yml       ← @ActiveProfiles("test") 일 때 추가 (DB 를 H2 로, 스키마를 `create-drop` 으로, 파일 저장 위치를 `build/test-storage` 로 덮어씀)
```

2차 개발의 현장 서비스들은 `application-prod.yml` 을 jar 옆에 두고 `prod` 프로필로 실행하는 방식을 씁니다 — 같은 원리입니다.

> 덮어쓰기 우선순위(높은 것이 이김): 실행 인자 `--server.port=9090` > 환경 변수 > 프로필 yml > 기본 yml

### 정리

- 환경마다 다른 값과 비밀 값은 코드 밖(yml, 환경 변수)에 둔다.
- 묶음 설정은 `@ConfigurationProperties` record 로, 값 하나는 `@Value` 로.
- 프로필로 상황별 설정을 덮어쓴다.

### 확인 문제

14-1. 운영 서버에서 로그인 잠금 시간을 30분으로 바꾸고 싶습니다. 코드를 고치지 않고 할 수 있는 방법을 두 가지 말해 보세요.

---

## 15장. 요청 한 건의 여행 — 서블릿, 필터, DispatcherServlet

### 이야기

[목차의 전체 지도](../backend-concepts.md#전체-지도-요청-하나의-여행)를 이제 자세히 볼 차례입니다. `PUT /api/v1/updates/12` 요청이 컨트롤러 메서드에 도착하기까지 무엇을 지나갈까요?

### 개념: 단계별로 따라가기

```
① 톰캣          : TCP 연결을 받고 HTTP 글자를 해석해 HttpServletRequest 객체로 만듦, 스레드 배정
② 필터 체인     : 서블릿 앞에서 요청을 검사·가공 (스프링 시큐리티가 여기 있음)
③ DispatcherServlet : 스프링 MVC 의 정문. 모든 요청이 여기로 들어옴
   ├ HandlerMapping   : 주소+메서드로 "UpdateController.updateMeta()" 를 찾음
   ├ ArgumentResolver : 파라미터 채우기
   │    @PathVariable Long id        ← 경로의 "12"
   │    @RequestBody UpdateMetaRequest ← 본문 JSON (HttpMessageConverter = Jackson)
   │    @Valid                         ← 검증 (실패하면 여기서 400)
   │    @AuthenticationPrincipal UserPrincipal ← 시큐리티가 찾아 둔 로그인 사용자
   ├ (프록시) @PreAuthorize 검사 → 컨트롤러 메서드 실행 → 서비스 → ...
   └ 반환값 처리 : UpdateResponse → Jackson 이 JSON 으로
④ 응답 전송
```

**서블릿(Servlet)** 은 자바 웹의 표준 규격으로, "요청 객체를 받아 응답 객체를 채우는 클래스" 입니다. 스프링 MVC 는 `DispatcherServlet` 이라는 서블릿 **하나**로 모든 요청을 받아 컨트롤러들에게 나눠 줍니다(**프런트 컨트롤러 패턴**).

### 필터 vs 인터셉터 vs AOP — 어디에 끼워 넣을까

요청을 가로채 공통 처리를 하는 자리는 세 군데입니다.

| 위치 | 시점 | 알 수 있는 것 | 우리 사용 |
|---|---|---|---|
| **필터(Filter)** | DispatcherServlet **전** (가장 바깥) | 요청/응답 원본 | 스프링 시큐리티 전체, `SessionUserRefreshFilter`, `IpRestrictionFilter`, `PasswordChangeRequiredFilter` |
| 인터셉터(HandlerInterceptor) | 컨트롤러 찾은 뒤, 실행 전후 | 어느 컨트롤러로 갈지 | (안 씀) |
| AOP(프록시) | 메서드 호출 전후 | 메서드 파라미터 | `@Transactional`, `@PreAuthorize` |

보안은 **가장 바깥(필터)** 에서 해야 합니다. 컨트롤러까지 오기 전에 막아야 하고, 화면 파일처럼 컨트롤러가 아닌 요청도 지나가기 때문입니다.

### 필터 만들기: OncePerRequestFilter

```java
// 실제 코드(config/security/PasswordChangeRequiredFilter.java)를 구조만 보이게 줄인 것
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");   // API 가 아니면 건너뜀
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        if (임시 비밀번호 상태 && 허용되지 않은 API) {
            errorWriter.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);   // 여기서 응답하고 끝 (다음으로 안 넘김)
            return;
        }
        chain.doFilter(request, response);    // 통과 → 다음 필터로
    }
}
```

- `chain.doFilter()` 를 부르면 다음 단계로 **통과**, 안 부르고 응답을 쓰면 **차단**입니다.
- `shouldNotFilter()` 로 대상이 아닌 요청은 건너뜁니다. 이걸 빠뜨려 운영에서만 화면 파일이 막혔던 버그가 8부 40장에 있습니다.
- **필터에서 난 오류는 `@RestControllerAdvice`(17장)가 잡지 못합니다.** 아직 DispatcherServlet 에 들어가기 전이기 때문입니다. 그래서 필터는 `SecurityErrorWriter` 로 `{ code, message }` 를 직접 씁니다.

필터의 **순서**도 중요합니다. `SecurityConfig` 에서 `addFilterBefore/After` 로 위치를 정합니다 (5부 27장, 30장).

### 정리

- 요청 = 톰캣 → 필터 체인 → DispatcherServlet → (파라미터 해석·검증) → 컨트롤러 → 응답 변환.
- 보안처럼 모든 요청에 필요한 것은 필터에서, 메서드 단위는 AOP 에서.
- 필터 오류는 컨트롤러 예외 처리기가 못 잡으니 직접 응답을 써야 한다.

### 확인 문제

15-1. 제목이 빈 `PUT /api/v1/updates/12` 요청은 ①~④ 중 어디서 거부될까요? CSRF 토큰이 없는 같은 요청은요?

---

## 16장. 컨트롤러 작성법

### 이야기

컨트롤러는 **HTTP 와 자바 사이의 통역사**입니다. HTTP 쪽 일(주소, 파라미터, 상태 코드)만 하고, 업무 판단은 서비스에 맡깁니다. 그래서 우리 컨트롤러는 대부분 한두 줄입니다.

### 개념: 자주 쓰는 어노테이션

```java
@RestController                         // 반환값을 JSON 으로 (화면 템플릿이 아님)
@RequestMapping("/api/v1/projects")     // 이 클래스 주소의 공통 앞부분
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping("/{id}")                                   // GET /api/v1/projects/3
    public ProjectResponse get(@PathVariable Long id) {    // 경로의 {id} → 3
        return projectService.findOne(id);                 // 반환값 → 200 + JSON
    }

    @PostMapping                                           // POST /api/v1/projects
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        ProjectResponse created = projectService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id()))  // 201 + Location 헤더
                .body(created);
    }
}
```

| 어노테이션 | 값을 어디서 가져오나 | 예 |
|---|---|---|
| `@PathVariable` | 경로 `{...}` | `/updates/{id}` |
| `@RequestParam` | 쿼리 문자열 / 폼 필드 | `?page=0&size=20` → `@RequestParam(defaultValue = "0") int page` |
| `@RequestBody` | 본문 JSON | 수정 요청 |
| `@ModelAttribute` | 폼 필드 여러 개 → 객체 | 업로드 요청의 버전·제목 (6부 32장) |
| `@AuthenticationPrincipal` | 로그인한 사용자 (시큐리티) | `UserPrincipal principal` |

### 반환 타입 고르기

- 그냥 객체를 돌려주면 **200 OK** + JSON.
- 상태 코드나 헤더를 정해야 하면 `ResponseEntity` — 생성은 201, 본문 없는 성공은 204, 다운로드는 `Content-Disposition` 헤더 (6부 32장).

### 컨트롤러에 넣지 않는 것

- 업무 규칙("같은 버전이면 안 됨"), 트랜잭션, DB 접근 → **서비스**
- 오류 응답 만들기 → **`GlobalExceptionHandler`** (17장)
- 로그인 사용자의 ID 를 요청 본문으로 받기 → **절대 안 됨.** 로그인 사용자는 항상 `@AuthenticationPrincipal` 로 받습니다. 본문으로 받으면 남의 ID 를 넣어 위조할 수 있습니다.

### 정리

- 컨트롤러는 HTTP ↔ 자바 통역만 한다. 판단은 서비스로.
- 값의 출처에 따라 `@PathVariable / @RequestParam / @RequestBody / @ModelAttribute` 를 고른다.
- 상태 코드·헤더가 필요하면 `ResponseEntity`.

### 확인 문제

16-1. 다운로드 이력 검색 `GET /api/v1/downloads?projectId=3&from=2026-10-01` 에서 `projectId` 는 어떤 어노테이션으로 받아야 할까요? 값이 없을 수도 있다면 어떻게 표시할까요?

---

## 17장. DTO · 입력 검증 · 예외 처리

컨트롤러 입구와 출구를 지키는 세 가지입니다. 입구에서 **DTO 로 받고 검증**하고, 출구에서 **DTO 로 내보내고**, 문제가 생기면 **한곳에서 오류 응답**을 만듭니다.

### 17.1. DTO 를 따로 두는 이유

엔티티(DB 테이블과 연결된 클래스, 4부)를 그대로 JSON 으로 내보내지 않고, 요청/응답 전용 클래스(DTO, Data Transfer Object)를 둡니다.

| 이유 | 예 |
|---|---|
| 숨길 값이 있음 | `UpdateInfo.fileKey`(서버 저장 경로), `AppUser.passwordHash` 는 절대 응답에 나가면 안 됨 |
| 지연 로딩 문제 | 엔티티를 그대로 직렬화하면 LAZY 필드를 건드려 예외가 나거나 쿼리가 폭발함 (4부 23장) |
| API 와 DB 구조 분리 | 테이블 컬럼을 바꿔도 API 응답 형식은 유지할 수 있음 |
| 요청 값 제한 | 등록 요청에 `developerId` 를 받지 않음 → 담당 개발자를 위조할 수 없음 (로그인 사용자로 자동 설정) |

DTO 는 거의 다 record 입니다 (2부 9장). 응답 DTO 에는 엔티티에서 만드는 정적 메서드를 둡니다.

```java
public record ProjectResponse(Long id, String name, String description, ...) {
    public static ProjectResponse from(Project project) { ... }   // 엔티티 → DTO 변환은 여기 한곳
}
```

### 17.2. 입력 검증 (@Valid)

DTO 필드에 규칙을 달고, 컨트롤러 파라미터에 `@Valid` 를 붙이면 **서비스에 도달하기 전에** 검증됩니다.

```java
public record UpdateCreateRequest(
        @NotBlank(message = "버전을 입력하세요.")
        @Size(max = 50, message = "버전은 50자 이하로 입력하세요.")
        @Pattern(regexp = "^[0-9A-Za-z][0-9A-Za-z.+_-]*$", message = "...")
        String version,
        ...
```

| 어노테이션 | 의미 |
|---|---|
| `@NotBlank` | null, 빈 문자열, 공백만 있는 문자열 거부 |
| `@NotNull` | null 거부 (숫자·enum 등) |
| `@Size(max = 200)` | 길이 제한 — **DB 컬럼 길이와 맞춤** (안 맞추면 DB 에서 오류) |
| `@Pattern(regexp = ...)` | 정규식 (버전 형식, 비밀번호 규칙 `PasswordPolicy.REGEX`) |
| `@Email` | 이메일 형식 |

검증 실패 → `MethodArgumentNotValidException` → `GlobalExceptionHandler` 가 400 `VALIDATION_FAILED` 와 **첫 번째 오류 메시지**로 응답합니다.

**검증의 역할 분담**

| 무엇 | 어디서 | 예 |
|---|---|---|
| 형식 (필수, 길이, 패턴) | DTO + `@Valid` | 제목 200자 이하 |
| DB 를 봐야 아는 것 | 서비스 | 같은 버전이 이미 있음 |
| 최종 방어 | DB 제약 | 유니크 제약 (2부 10장) |
| 사용자 편의 | 프런트 | 입력 중 실시간 안내 (8부 43장) |

### 17.3. 예외 처리 — 오류 응답은 한곳에서

```
Service: throw new ApiException(ErrorCode.DUPLICATE_VERSION)
   ↓ 예외가 컨트롤러 밖으로 나감 (중간에 아무도 안 잡음)
GlobalExceptionHandler (@RestControllerAdvice)
   ↓ ErrorCode → 상태 409 + { "code": "DUPLICATE_VERSION", "message": "이 프로젝트에 같은 버전이 ..." }
```

```java
@RestControllerAdvice                     // 모든 컨트롤러의 예외를 여기서 처리
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)   // 우리 업무 예외
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)      // 예상 못 한 모든 예외
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);    // 로그에는 자세히
        return toResponse(ErrorCode.INTERNAL_ERROR);   // 응답에는 "서버 오류가 발생했습니다." 만
    }
}
```

- 모든 오류 코드는 `ErrorCode` enum 한곳에서 관리 → HTTP 상태와 메시지가 같이 정해짐
- 컨트롤러·서비스에서 `try-catch` 로 응답을 만들 필요가 없음
- 스프링 기본 예외(404 없는 주소, 405 지원 안 하는 메서드, 413 파일 너무 큼)도 `ResponseEntityExceptionHandler` 를 상속해 같은 형식으로 맞춤
- 예상 못 한 예외는 **로그에 스택을 남기고, 응답에는 내부 정보를 노출하지 않음** (공격자에게 힌트를 주지 않기 위해)
- 필터 단계 오류는 여기서 못 잡음 → 15장

### 정리

- 입구·출구는 DTO. 엔티티는 밖으로 내보내지 않는다.
- 형식 검증은 `@Valid`, DB 를 봐야 하는 검증은 서비스, 최종 방어는 DB 제약.
- 오류는 `ApiException(ErrorCode)` 로 던지고 `@RestControllerAdvice` 한곳에서 응답으로 바꾼다.

### 확인 문제

17-1. 비밀번호 규칙(영문+숫자 8~64자)은 프런트에서도 검사하는데, 백엔드 `@Pattern` 을 빼도 될까요?
17-2. 예상 못 한 예외의 메시지(`e.getMessage()`)를 그대로 응답에 넣으면 어떤 위험이 있을까요?

---

[← 2부](02-java-basics.md) · [목차](../backend-concepts.md) · [4부. 데이터베이스와 JPA →](04-database-jpa.md)
