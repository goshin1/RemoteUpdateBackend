# Backend 개념 노트

RemoteUpdate를 만들면서 실제로 쓴 Spring Boot / JPA 개념을 정리한 공부용 노트입니다.
3계층(Controller · Service · Repository)과 Entity · DTO · REST API는 안다는 전제에서, **그다음 단계**의 개념을 다룹니다.
보안(세션, CSRF, BCrypt 등)은 [security-guide.md](security-guide.md)에 따로 있습니다.

각 항목은 "무엇인가 → 왜 쓰나 → 우리 코드 어디에" 순서입니다. 단계가 진행될 때마다 아래에 계속 추가합니다.

## 목차

1. [스프링 빈과 의존성 주입(DI)](#1-스프링-빈과-의존성-주입di)
2. [@Transactional — 트랜잭션](#2-transactional--트랜잭션)
3. [더티 체킹 — save를 안 불러도 저장되는 이유](#3-더티-체킹--save를-안-불러도-저장되는-이유)
4. [지연 로딩(LAZY)과 open-in-view](#4-지연-로딩lazy과-open-in-view)
5. [N+1 문제와 @EntityGraph](#5-n1-문제와-entitygraph)
6. [Entity 설계 습관](#6-entity-설계-습관)
7. [DTO를 따로 두는 이유 (record)](#7-dto를-따로-두는-이유-record)
8. [입력 검증 (@Valid)](#8-입력-검증-valid)
9. [예외 처리 계층](#9-예외-처리-계층)
10. [설정 값 주입 (@ConfigurationProperties, 프로필)](#10-설정-값-주입-configurationproperties-프로필)
11. [페이징 (Pageable)](#11-페이징-pageable)
12. [동적 검색 조건 (Specification)](#12-동적-검색-조건-specification)
13. [파일 업로드·다운로드 (multipart, 스트리밍)](#13-파일-업로드다운로드-multipart-스트리밍)
14. [트랜잭션 이후에 할 일 (TransactionSynchronization)](#14-트랜잭션-이후에-할-일-transactionsynchronization)
15. [인터페이스로 구현 숨기기 (FileStorage)](#15-인터페이스로-구현-숨기기-filestorage)
16. [테스트 (@SpringBootTest, MockMvc, H2)](#16-테스트-springboottest-mockmvc-h2)
17. [용어 사전](#17-용어-사전)

---

## 1. 스프링 빈과 의존성 주입(DI)

**무엇인가**
`@Service`, `@Repository`, `@Component`, `@Controller`가 붙은 클래스는 스프링이 앱 시작 때 **객체를 하나만 만들어서 보관**합니다. 이 객체를 **빈(Bean)**이라고 부릅니다.
다른 클래스가 그 빈을 필요로 하면, 직접 `new` 하지 않고 **생성자 파라미터로 받습니다**. 스프링이 알아서 넣어 줍니다. 이것이 **의존성 주입(DI)**입니다.

```java
@Service
@RequiredArgsConstructor          // Lombok: final 필드를 받는 생성자를 자동 생성
public class UpdateService {
    private final UpdateInfoRepository updateRepository;   // 스프링이 주입
    private final FileStorage fileStorage;                 // 인터페이스 타입으로 받음
}
```

**왜 쓰나**
- 클래스끼리 직접 `new`로 묶이지 않아서, 구현을 바꾸거나(15번) 테스트에서 가짜로 갈아끼우기 쉽습니다.
- 빈은 기본적으로 **싱글톤**(앱 전체에 하나)입니다. 그래서 빈 안에 **요청마다 바뀌는 값을 필드로 두면 안 됩니다**. 여러 요청이 동시에 같은 객체를 씁니다.

**생성자 주입을 쓰는 이유**: `@Autowired` 필드 주입보다 `final`로 불변이 보장되고, 테스트에서 `new`로 직접 만들 수도 있습니다 (`ClientIpResolverTest`가 그 예).

**`@Bean`**: 직접 만든 클래스가 아닌 것(라이브러리 클래스)을 빈으로 등록할 때 `@Configuration` 클래스 안에 메서드로 만듭니다.
> 예: `SecurityConfig.passwordEncoder()`가 `BCryptPasswordEncoder`를 빈으로 등록

---

## 2. @Transactional — 트랜잭션

**무엇인가**
여러 DB 작업을 **하나로 묶어서 전부 성공하거나 전부 취소**되게 합니다.
업데이트 등록은 `update_info` 저장 + `update_history` 저장 두 가지인데, 둘 중 하나만 들어가면 데이터가 어긋납니다.

```java
@Transactional
public UpdateResponse create(...) {
    updateRepository.saveAndFlush(info);       // 1
    historyRepository.save(history);           // 2  ← 여기서 예외가 나면 1도 취소(롤백)
}
```

**알아둘 규칙**

| 규칙 | 설명 |
|---|---|
| RuntimeException이면 롤백 | 체크 예외(IOException 등)는 기본적으로 롤백하지 않음. 우리는 `ApiException`(RuntimeException)을 씀 |
| `readOnly = true` | 조회 전용. 변경 감지(3번)를 건너뛰어 조금 빠르고, 실수로 수정하는 것을 막음. 클래스에 기본으로 걸고 쓰기 메서드에만 `@Transactional`을 다시 붙임 |
| `noRollbackFor` | 예외가 나도 커밋. **로그인 실패 횟수 기록**에 사용 (예외를 던지지만 실패 횟수는 저장돼야 함) |
| 같은 클래스 안 호출 | `this.methodA()`가 `@Transactional methodB()`를 부르면 트랜잭션이 적용되지 않음 (프록시를 거치지 않기 때문). 다른 빈으로 분리해야 함 |

> 코드: `AuthService.authenticate()` (`noRollbackFor`), `UpdateService` (클래스 `readOnly` + 메서드별 쓰기)

---

## 3. 더티 체킹 — save를 안 불러도 저장되는 이유

**무엇인가**
트랜잭션 안에서 DB에서 꺼낸 엔티티의 값을 바꾸면, 트랜잭션이 끝날 때 JPA가 **바뀐 부분을 찾아 UPDATE를 자동으로 실행**합니다.

```java
@Transactional
public UpdateResponse updateMeta(Long id, UpdateMetaRequest req, ...) {
    UpdateInfo info = getUpdate(id);          // DB에서 조회 (JPA가 "원래 값"을 기억)
    info.updateMeta(req.title(), req.content());  // 값만 바꿈. save() 호출 없음
    return UpdateResponse.from(info);
}   // ← 트랜잭션 종료 시점에 UPDATE 쿼리 자동 실행
```

**주의**: 트랜잭션 밖에서 바꾸면 반영되지 않습니다. 새 엔티티(아직 DB에 없는 것)는 `save()`가 필요합니다.

---

## 4. 지연 로딩(LAZY)과 open-in-view

**지연 로딩**
`UpdateInfo`는 `developer`(AppUser)를 가지고 있지만, `UpdateInfo`를 조회할 때 사용자까지 바로 가져오지 않습니다.
`info.getDeveloper().getName()`처럼 **실제로 쓰는 순간** 쿼리를 날립니다. 쓰지 않으면 쿼리도 없습니다.

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
private AppUser developer;
```

`@ManyToOne`의 기본값은 EAGER(즉시 로딩)라서, 우리는 **항상 LAZY로 명시**합니다. EAGER는 필요 없을 때도 조인이 붙어 쿼리가 무거워집니다.

**open-in-view = false**
스프링 부트 기본값(true)은 DB 연결을 **응답이 다 나갈 때까지** 열어 둬서, 컨트롤러에서도 지연 로딩이 됩니다. 편하지만 요청이 끝날 때까지 DB 연결을 붙잡습니다.
우리는 `false`로 꺼 두었습니다. 그래서 **지연 로딩은 서비스의 트랜잭션 안에서 끝내야** 합니다.
→ 서비스에서 엔티티를 DTO로 바꿔서(`UpdateResponse.from(info)`) 돌려주는 이유입니다. 트랜잭션 밖에서 `getDeveloper().getName()`을 부르면 `LazyInitializationException`이 납니다.

> 코드: `application.yml`의 `spring.jpa.open-in-view: false`

---

## 5. N+1 문제와 @EntityGraph

**문제**
업데이트 20개를 조회한 뒤(쿼리 1번), 각 업데이트의 개발자 이름을 꺼내면 지연 로딩 때문에 개발자 조회 쿼리가 **20번 더** 나갑니다. 1 + N번이라 "N+1 문제"입니다.

**해결: @EntityGraph**
"이 조회를 할 때는 developer도 같이 가져와"라고 지정하면 조인 한 번으로 끝납니다.

```java
@EntityGraph(attributePaths = "developer")
Page<UpdateInfo> findByProjectId(Long projectId, Pageable pageable);

@EntityGraph(attributePaths = {"update", "update.project"})   // 두 단계도 가능
Page<DownloadHistory> findAll(Specification<DownloadHistory> spec, Pageable pageable);
```

엔티티에는 LAZY를 걸어 두고, **목록 조회처럼 함께 필요한 경우에만** 그때그때 같이 가져오는 것이 기본 전략입니다.

> 코드: `UpdateInfoRepository`, `DownloadHistoryRepository`, `UpdateHistoryRepository`, `SetupGuideRepository`

---

## 6. Entity 설계 습관

| 습관 | 이유 | 예 |
|---|---|---|
| 기본 생성자는 `protected` | JPA는 기본 생성자가 필요하지만, 외부에서 빈 객체를 만들지 못하게 | `@NoArgsConstructor(access = AccessLevel.PROTECTED)` |
| setter 대신 의미 있는 메서드 | "무엇을 하는지"가 이름에 드러나고, 규칙을 한곳에 모음 | `user.recordLoginFailure(...)`, `info.changeStatus(...)` |
| 생성은 정적 팩토리 메서드 | 필수 값과 초기 상태(ACTIVE 등)를 한곳에서 보장 | `UpdateInfo.create(...)` |
| `@Enumerated(EnumType.STRING)` | 기본값(ORDINAL)은 숫자로 저장돼서, enum 순서를 바꾸면 기존 데이터 의미가 바뀜 | `Role`, `UpdateStatus` |
| `updatable = false` | 등록 후 바꾸면 안 되는 값을 DB 수준에서 막음 | 업데이트 파일 키·체크섬·버전 |
| 공통 컬럼은 상속 | `created_at`, `updated_at`을 모든 엔티티에 반복하지 않음 | `BaseTimeEntity` + JPA Auditing |

**JPA Auditing**: `@CreatedDate`, `@LastModifiedDate`를 붙이면 저장·수정 시각을 자동으로 채웁니다. `@EnableJpaAuditing`(우리는 `JpaConfig`)이 켜져 있어야 동작합니다.

---

## 7. DTO를 따로 두는 이유 (record)

엔티티를 그대로 JSON으로 내보내지 않고, 요청/응답 전용 클래스(DTO)를 둡니다.

| 이유 | 예 |
|---|---|
| 숨길 값이 있음 | `UpdateInfo.fileKey`(서버 저장 경로), `AppUser.passwordHash`는 절대 응답에 나가면 안 됨 |
| 지연 로딩 문제 | 엔티티를 그대로 직렬화하면 LAZY 필드를 건드려 예외가 나거나 쿼리가 폭발함 |
| API와 DB 구조 분리 | 테이블 컬럼을 바꿔도 API 응답 형식은 유지할 수 있음 |
| 요청 값 제한 | 등록 요청에 `developerId`를 받지 않음 → 담당 개발자를 위조할 수 없음 (로그인 사용자로 자동 설정) |

**record**: Java 16+의 불변 데이터 클래스. 생성자·getter·equals·toString이 자동입니다. DTO에 딱 맞습니다.

```java
public record UpdateMetaRequest(
        @NotBlank(message = "제목을 입력하세요.") String title,
        @Size(max = 2000) String content) { }
```

---

## 8. 입력 검증 (@Valid)

DTO 필드에 규칙을 달고, 컨트롤러 파라미터에 `@Valid`를 붙이면 **서비스에 도달하기 전에** 검증됩니다.

```java
@PutMapping("/updates/{id}")
public UpdateResponse updateMeta(@PathVariable Long id, @Valid @RequestBody UpdateMetaRequest request, ...)
```

| 어노테이션 | 의미 |
|---|---|
| `@NotBlank` | null, 빈 문자열, 공백만 있는 문자열 거부 |
| `@NotNull` | null 거부 |
| `@Size(max = 200)` | 길이 제한 (DB 컬럼 길이와 맞춤) |
| `@Pattern(regexp = ...)` | 정규식 (버전 형식, 비밀번호 규칙) |

검증 실패 → `MethodArgumentNotValidException` → `GlobalExceptionHandler`가 400 `VALIDATION_FAILED`로 변환합니다.

**검증의 역할 분담**: 형식 검증(길이, 필수)은 DTO에서, **DB를 봐야 아는 검증**(버전 중복, 이름 중복)은 서비스에서 합니다.

---

## 9. 예외 처리 계층

```
Service: throw new ApiException(ErrorCode.DUPLICATE_VERSION)
   ↓ (예외가 컨트롤러 밖으로 나감)
GlobalExceptionHandler (@RestControllerAdvice): ErrorCode → { "code": "DUPLICATE_VERSION", "message": "..." } + 409
```

- 모든 오류 코드는 `ErrorCode` enum 한곳에서 관리 → HTTP 상태와 메시지가 같이 정해짐
- 컨트롤러에서 `try-catch`로 응답을 만들 필요가 없음
- 스프링 기본 예외(404, 405, 413 등)도 `ResponseEntityExceptionHandler`를 상속해 같은 형식으로 맞춤
- 예상 못 한 예외는 500 `INTERNAL_ERROR`로 응답하고 **로그에는 스택을 남김** (응답에는 내부 정보를 노출하지 않음)

> 필터 단계(컨트롤러 이전) 오류는 여기서 못 잡습니다 → security-guide.md 1번 참고

---

## 10. 설정 값 주입 (@ConfigurationProperties, 프로필)

**@ConfigurationProperties**
`application.yml`의 `app.*` 값을 타입이 있는 객체로 받습니다. `@Value("${...}")`를 여기저기 쓰는 것보다 한곳에 모이고, `Duration`(15m), `Set<String>` 같은 변환도 자동입니다.

```yaml
app:
  security:
    login:
      max-failures: 5
      lock-duration: 15m
```
```java
@ConfigurationProperties(prefix = "app")
public record AppProperties(Storage storage, Upload upload, Security security, Seed seed) { ... }
// 사용: appProperties.security().login().maxFailures()
```

**환경 변수로 덮어쓰기**: `${DB_PASSWORD:}` → 환경 변수 `DB_PASSWORD`가 있으면 그 값, 없으면 빈 값. 비밀 값을 파일에 쓰지 않는 방법입니다.

**프로필**: `application-test.yml`은 `test` 프로필에서만 추가로 읽힙니다. 테스트 클래스의 `@ActiveProfiles("test")`가 켭니다. 기본 설정을 유지한 채 DB만 H2로 바꾸는 식으로 **필요한 부분만 덮어씁니다**.

---

## 11. 페이징 (Pageable)

```java
Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
Page<UpdateInfo> result = updateRepository.findByProjectId(projectId, pageable);
```

- Repository 메서드에 `Pageable`을 넘기면 스프링 데이터가 `LIMIT/OFFSET` 쿼리와 **전체 개수 쿼리**를 함께 실행합니다.
- 정렬에 `id`를 두 번째 기준으로 넣은 이유: `createdAt`이 같은 행이 있으면 페이지마다 순서가 바뀔 수 있어서 항상 같은 순서를 보장하려고.
- `size`는 서버에서 최대 100으로 제한 → `size=1000000` 같은 요청으로 서버를 괴롭히지 못하게.
- `Page`를 그대로 JSON으로 내보내지 않고 `PageResponse`로 바꿉니다 (스프링 버전마다 `Page`의 JSON 구조가 달라질 수 있음).

---

## 12. 동적 검색 조건 (Specification)

다운로드 이력은 프로젝트·업데이트·사용자·기간 중 **아무 조합**으로나 검색할 수 있어야 합니다.
메서드 이름 방식(`findByProjectIdAndUserIdAnd...`)으로는 조합마다 메서드가 필요해 감당이 안 됩니다.

**Specification**: 조건 하나를 "조각"으로 만들고, 값이 없으면 `null`을 돌려 **그 조건을 빼 버립니다**.

```java
static Specification<DownloadHistory> userId(Long userId) {
    return (root, query, cb) -> userId == null ? null : cb.equal(root.get("user").get("id"), userId);
}

Specification<DownloadHistory> spec = Specification.allOf(
        projectId(projectId), updateId(updateId), userId(userId), downloadedBetween(from, to));
```

Repository는 `JpaSpecificationExecutor`를 상속하면 `findAll(spec, pageable)`을 쓸 수 있습니다.
(더 복잡해지면 QueryDSL이라는 라이브러리를 많이 쓰지만, 이 정도는 Specification으로 충분합니다.)

**날짜 조건 팁**: "10/3 ~ 10/3" 검색은 `>= 10/3 00:00` 그리고 `< 10/4 00:00`으로 바꿉니다. `<= 10/3 23:59:59`로 하면 밀리초 단위 데이터가 빠질 수 있습니다.

> 코드: `update/DownloadHistorySpecs.java`, `DownloadService.search()`

---

## 13. 파일 업로드·다운로드 (multipart, 스트리밍)

**업로드 = multipart/form-data**
JSON은 파일을 담을 수 없어서, 텍스트 필드와 파일을 함께 보내는 `multipart/form-data` 형식을 씁니다.

```java
@PostMapping(value = "/projects/{projectId}/updates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<UpdateResponse> create(
        @Valid @ModelAttribute UpdateCreateRequest request,              // 텍스트 필드 → record
        @RequestParam(value = "file", required = false) MultipartFile file) // 파일
```

- `@RequestBody`(JSON)가 아니라 `@ModelAttribute`(폼 필드)로 받습니다.
- 크기 제한은 `spring.servlet.multipart.max-file-size`. 기본값이 1MB라 반드시 늘려야 합니다.
- 일정 크기(2MB) 이상은 메모리가 아니라 임시 파일로 받습니다 (`file-size-threshold`).

**저장하면서 체크섬 계산**
파일을 다 저장한 뒤 다시 읽어서 해시를 구하면 500MB를 두 번 읽습니다. `DigestInputStream`으로 **저장하는 흐름 중간에서 동시에 계산**합니다.

**다운로드 = 스트리밍**
파일 전체를 `byte[]`로 메모리에 올리지 않고, `Resource`를 응답 본문으로 주면 스프링이 조금씩 읽어서 보냅니다.
500MB 파일 10명이 동시에 받아도 서버 메모리는 거의 늘지 않습니다.

```java
return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''...")   // 저장 대화상자 + 한글 파일명
        .contentLength(size)
        .body(resource);
```

**트랜잭션 밖에서 보내기**: 권한 확인·이력 기록은 트랜잭션 안에서 끝내고 `DownloadTarget`만 돌려준 뒤, 컨트롤러에서 전송합니다. 몇 분 걸리는 전송 동안 DB 연결을 잡고 있으면 연결 풀(기본 10개)이 금방 바닥납니다.

> 코드: `UpdateController.create()`, `LocalFileStorage.store()`, `DownloadService.prepare()`, `common/web/FileResponses`

---

## 14. 트랜잭션 이후에 할 일 (TransactionSynchronization)

**문제**: 업데이트 등록 순서는 "파일 저장 → DB 저장"입니다. 파일은 디스크라 **DB 롤백이 되돌려 주지 않습니다**. DB 저장이 실패하면 디스크에 주인 없는 파일이 남습니다.

**해결**: "트랜잭션이 끝나면 이걸 해 줘"를 등록합니다.

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) {
            fileStorage.delete(key);     // 롤백됐으면 방금 저장한 파일 삭제
        }
    }
});
```

반대로 가이드 첨부 교체에서는 `afterCommit()`에서 **이전 파일**을 지웁니다. 커밋 전에 지웠다가 롤백되면 DB는 이전 파일을 가리키는데 파일은 없어지기 때문입니다.

**원칙**: DB 밖의 부수 효과(파일, 메일, 외부 API)는 **커밋이 확정된 뒤**에 하거나, 실패 시 되돌릴 방법을 마련한다.

> 코드: `UpdateService.deleteFileIfRolledBack()`, `GuideService.store()`, `GuideService.afterCommit()`

---

## 15. 인터페이스로 구현 숨기기 (FileStorage)

```java
public interface FileStorage {
    StoredFile store(FileCategory category, String originalName, InputStream content);
    Resource load(String key);
    void delete(String key);
}

@Component
public class LocalFileStorage implements FileStorage { ... }   // 지금: 로컬 디스크
// 나중에: S3FileStorage implements FileStorage           // 클라우드로 옮길 때 이것만 추가
```

서비스는 `FileStorage` 타입만 알고, 실제로 디스크에 쓰는지 S3에 쓰는지 모릅니다.
기획서의 "추후 클라우드 이전을 고려한 파일 저장소 추상화"가 이것입니다. 구현을 바꿔도 `UpdateService`, `GuideService`는 한 줄도 바뀌지 않습니다.

---

## 16. 테스트 (@SpringBootTest, MockMvc, H2)

| 도구 | 역할 |
|---|---|
| `@SpringBootTest` | 실제 앱과 같은 스프링 컨텍스트(모든 빈)를 띄움 |
| `@AutoConfigureMockMvc` + `MockMvc` | 서버를 진짜로 띄우지 않고 HTTP 요청을 흉내 내서 컨트롤러~DB까지 전부 통과시킴 |
| H2 (MariaDB 모드) | 메모리 DB. MariaDB 설치 없이 `gradlew test`가 돌아감 |
| `@ActiveProfiles("test")` | `application-test.yml` 적용 (DB를 H2로) |
| `spring-security-test`의 `csrf()` | 테스트 요청에 올바른 CSRF 토큰을 붙여 줌 |

```java
mvc.perform(post("/api/v1/projects").session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("A"));
```

**컨텍스트 캐싱**: 설정이 같은 테스트 클래스들은 스프링 컨텍스트를 **한 번만 띄워 재사용**합니다. 빠르지만 DB도 공유되므로, 테스트마다 이메일·프로젝트명에 UUID를 붙여 서로 겹치지 않게 했습니다.
`@DirtiesContext`를 붙이면 새 컨텍스트를 띄웁니다 (`CsrfCookieTest`처럼 공유 상태를 피해야 할 때).

**공통 기능은 부모 클래스로**: 사용자 생성과 로그인은 `ApiTestSupport`에 두고 각 테스트가 상속합니다.

> 실행: `.\gradlew.bat test` → 결과 보고서 `build/reports/tests/test/index.html`

---

## 17. 용어 사전

| 용어 | 한 줄 설명 |
|---|---|
| 빈(Bean) | 스프링이 만들어 보관·주입하는 객체 |
| DI | 필요한 객체를 직접 만들지 않고 외부(스프링)에서 받는 것 |
| 프록시 | `@Transactional`, `@PreAuthorize`가 동작하도록 스프링이 빈을 감싸 만든 대리 객체 |
| 영속성 컨텍스트 | 트랜잭션 동안 JPA가 엔티티를 보관·추적하는 공간 (더티 체킹의 근거) |
| flush | 영속성 컨텍스트의 변경을 DB에 SQL로 보내는 것 (`saveAndFlush`는 즉시 보냄 → 유니크 제약 위반을 그 자리에서 잡을 수 있음) |
| 지연 로딩 | 연관 엔티티를 실제로 쓸 때 조회 |
| N+1 | 목록 1번 + 항목마다 1번씩 추가 쿼리가 나가는 문제 |
| DTO | 계층·API 사이에 데이터를 옮기는 전용 객체 |
| 멱등(idempotent) | 같은 요청을 여러 번 보내도 결과가 같음. GET/PUT은 멱등, POST는 아님. 같은 상태로 PATCH하면 이력을 남기지 않게 한 것도 같은 생각 |
| 스트리밍 | 데이터를 한 번에 메모리에 올리지 않고 조금씩 흘려보내는 것 |
| 체크섬(SHA-256) | 파일 내용으로 계산한 고유 값. 1바이트만 달라도 완전히 바뀌어 손상·변조 확인에 씀 |
| 커넥션 풀 | DB 연결을 미리 만들어 두고 돌려 쓰는 것 (HikariCP, 기본 10개) |
