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
18. [프런트와 백엔드의 역할 분담](#18-프런트와-백엔드의-역할-분담)
19. [E2E 테스트 — 브라우저로 끝까지 확인하기](#19-e2e-테스트--브라우저로-끝까지-확인하기)
20. [세션 사본 문제 — 권한 변경을 즉시 반영하기](#20-세션-사본-문제--권한-변경을-즉시-반영하기)
21. [작은 것들: @Value, SecureRandom, LIKE 이스케이프, 이중 안전장치](#21-작은-것들-value-securerandom-like-이스케이프-이중-안전장치)
22. [경쟁 조건(race condition)](#22-경쟁-조건race-condition)
23. [개발 환경과 운영 환경은 다르다](#23-개발-환경과-운영-환경은-다르다)
24. [성능을 숫자로 확인하기](#24-성능을-숫자로-확인하기)

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

---

## 18. 프런트와 백엔드의 역할 분담

Phase 4에서 Vue 화면을 붙이면서 "같은 검사를 왜 양쪽에서 하나?"가 자주 나옵니다.

| 검사 | 프런트(Vue) | 백엔드(Spring) |
|---|---|---|
| 로그인 여부 | 라우터 가드가 로그인 화면으로 보냄 | 필터가 401 |
| 역할 | 개발자 메뉴·상태 필터를 숨김 | `@PreAuthorize`가 403 |
| 비밀번호 규칙 | 입력 중에 바로 안내 | `@Pattern`이 400 |
| 비활성 업데이트 | 다운로드 버튼을 안 보여 줌 | 다운로드 요청 자체를 403 |

**원칙: 프런트 검사는 "편의", 백엔드 검사는 "보안".**
브라우저 코드는 사용자가 개발자 도구로 얼마든지 바꾸거나 건너뛸 수 있습니다. 프런트에서 버튼을 숨겨도 주소를 직접 입력하면 요청은 갑니다.
그래서 **보안 판단은 반드시 서버가** 하고, 프런트는 사용자가 헛걸음하지 않게 미리 안내하는 역할입니다.

**서버 응답이 진실**: 프런트의 로그인 상태(Pinia `auth` 스토어)는 서버 세션의 "사본"입니다. 세션이 만료되면 서버는 401을 주고, 프런트는 그걸 받아(axios 응답 인터셉터) 사본을 지우고 로그인 화면으로 보냅니다.

**API 응답 형식을 맞춰 두면 편한 점**: 모든 오류가 `{ code, message }`라서 프런트는 `errorMessage(e)` 하나로 서버 메시지를 그대로 보여 줄 수 있고, 특정 상황은 `code`로 분기합니다 (예: `PASSWORD_CHANGE_REQUIRED` → 비밀번호 화면).

> 코드: Frontend `src/router/index.ts`(가드), `src/api/http.ts`(인터셉터), `src/stores/auth.ts`

---

## 19. E2E 테스트 — 브라우저로 끝까지 확인하기

| 종류 | 범위 | 우리 프로젝트 |
|---|---|---|
| 단위 테스트 | 클래스 하나 | `ClientIpResolverTest` |
| 통합 테스트 | 스프링 전체 + DB (HTTP는 흉내) | `AuthApiTest`, `UpdateApiTest` 등 (MockMvc) |
| **E2E 테스트** | 실제 브라우저 → 프런트 → 백엔드 → DB | Phase 4에서 Playwright로 수행 |

MockMvc 테스트는 빠르고 정확하지만 **브라우저가 실제로 쿠키를 어떻게 다루는지**는 확인하지 못합니다.
Phase 4에서는 백엔드(H2)와 Vite 개발 서버를 띄우고, Playwright(브라우저 자동화 도구)로 실제 Chromium을 조작해 확인했습니다.

- 로그인 → 비밀번호 변경 강제 → 변경 후 진입
- CSRF 쿠키 자동 발급 → 헤더 첨부 (POST가 통과하는지)
- 다운로드한 파일 내용과 SHA-256 체크섬이 화면 값과 같은지
- 가이드에 넣은 `<script>`가 실행되지 않는지 (XSS)
- 세션 쿠키를 지운 뒤 이동 → 로그인 화면 + 원래 화면으로 복귀
- 휴대폰 폭에서 가로 스크롤이 생기지 않는지

여기서만 발견된 문제가 실제로 있었습니다: 표 안의 숨김 텍스트(`position: absolute`) 때문에 휴대폰 화면이 가로로 넘쳤습니다. 단위·통합 테스트로는 찾을 수 없는 종류입니다.

---

## 20. 세션 사본 문제 — 권한 변경을 즉시 반영하기

**문제**
로그인할 때 세션에 사용자 정보(역할 등)를 **복사해** 넣습니다. 이후 요청은 DB 가 아니라 이 사본으로 권한을 판단합니다.
그래서 관리자가 계정을 비활성화하거나 역할을 낮춰도, 이미 로그인한 사람은 세션이 끝날 때까지(최대 30분) 옛 권한으로 계속 쓸 수 있습니다.

**해결 방법 비교**

| 방법 | 장점 | 단점 |
|---|---|---|
| 그냥 둔다 (세션 만료까지 기다림) | 간단 | 퇴사자·강등자가 최대 30분 더 사용 |
| 변경할 때 그 사용자의 세션을 찾아 만료 (SessionRegistry) | DB 조회 없음 | 세션 목록 관리가 필요, 서버 여러 대면 복잡 |
| **요청마다 DB 에서 다시 확인 (우리 선택)** | 단순하고 확실, 역할 변경까지 반영 | 요청마다 기본키 조회 1번 |

우리는 사내 시스템이라 요청 수가 많지 않아 세 번째를 택했습니다.
`SessionUserRefreshFilter`가 권한 검사 **직전에** DB 값을 읽어, 비활성이면 세션을 끊고(401) 바뀐 점이 있으면 세션을 새 값으로 바꿉니다.

**필터 위치가 중요한 이유**: 권한 검사(AuthorizationFilter) **뒤에** 두면, 이미 옛 역할로 통과한 다음에 갱신하게 됩니다. 반드시 앞에 둬야 합니다.

> 코드: `config/security/SessionUserRefreshFilter.java`, `SecurityConfig`의 `addFilterBefore(...)`
> 테스트: `UserAdminApiTest`의 "비활성화하면_이미_로그인한_세션도_즉시_끊긴다", "역할을_낮추면_다음_요청부터_바로_권한이_줄어든다"

---

## 21. 작은 것들: @Value, SecureRandom, LIKE 이스케이프, 이중 안전장치

**@Value vs @ConfigurationProperties**
- `@Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize` → 설정값 **하나**를 바로 주입. 스프링 기본 설정을 읽을 때 간편
- 우리 앱 설정(`app.*`)처럼 **묶음**은 `@ConfigurationProperties` 가 낫습니다 (10번)
> 코드: `config/web/UploadPolicyController.java`

**SecureRandom**
`java.util.Random`은 내부 상태를 알면 다음 값을 예측할 수 있습니다. 비밀번호·토큰처럼 **추측되면 안 되는 값**은 반드시 `SecureRandom`을 씁니다.
> 코드: `user/TemporaryPasswordGenerator.java`

**LIKE 이스케이프**
검색어를 `LIKE '%검색어%'`에 넣을 때, 사용자가 `%`나 `_`를 입력하면 "아무 글자"로 해석되어 전부 검색됩니다.
특수문자 앞에 `\`를 붙이고 `cb.like(..., pattern, '\\')`로 이스케이프 문자를 지정하면 글자 그대로 찾습니다.
(SQL 인젝션은 아닙니다 — JPA 는 값을 항상 파라미터로 보내므로 안전. 단지 검색 결과가 틀려지는 문제)
> 코드: `user/UserSpecs.keyword()`

**이중 안전장치**
`/api/v1/admin/**`는 URL 규칙으로 이미 ADMIN 만 통과하지만, 컨트롤러 메서드에도 `@PreAuthorize("hasRole('ADMIN')")`를 붙였습니다.
나중에 누가 URL 규칙을 바꾸거나 메서드 주소를 옮겨도 권한이 빠지지 않습니다. 보안은 "한 곳만 믿지 않는 것"이 원칙입니다.

**비즈니스 규칙으로 막는 것들** (`UserAdminService`)
- 관리자는 **본인**의 역할·사용 여부를 바꿀 수 없음 → 실수로 스스로를 잠그는 것 방지
- 마지막 활성 관리자는 강등·중지 불가 → 아무도 관리할 수 없는 상태 방지
- 계정은 삭제하지 않고 중지 → 이력의 "누가"가 사라지지 않음

---

## 22. 경쟁 조건(race condition)

**두 작업의 순서가 그때그때 달라서** 결과가 달라지는 버그입니다. 재현이 잘 안 돼서 찾기 어렵습니다.

**이번에 실제로 만난 예 (Frontend)**
업로드 화면을 열면 서버에서 업로드 정책(허용 확장자)을 받아오는데, 그 응답이 오기 **전에** 파일을 고르면 확장자 검사를 건너뛰었습니다.
E2E 테스트에서 페이지를 열자마자 파일을 고르는 순서일 때만 실패해서 발견했습니다.
해결: 검사 전에 정책 조회 Promise 를 기다리게 함 (`useUploadPolicy().ready()`).
(서버가 최종 검사를 하므로 보안 문제는 아니었고, 안내가 늦게 나오는 문제)

**Backend 의 예 (이미 대비해 둔 것)**
같은 버전을 두 사람이 **동시에** 등록하면, 둘 다 "중복 없음"을 확인한 뒤 둘 다 저장할 수 있습니다.
코드의 `existsBy...` 검사만으로는 막을 수 없고, **DB 유니크 제약**이 최종 방어선입니다.
`saveAndFlush`로 그 자리에서 제약 위반을 잡아 409 로 바꿉니다.
> 코드: `UpdateService.create()`

---

## 23. 개발 환경과 운영 환경은 다르다

Phase 6 에서 운영 방식(Spring 하나가 화면까지 제공)으로 띄우자마자, 개발 중에는 한 번도 안 보이던 버그가 나왔습니다.

| | 개발 | 운영 |
|---|---|---|
| 화면 파일(js, css) | Vite 개발 서버(5173)가 제공 | **Spring(8080)이 제공 → 보안 필터를 통과** |
| DB | 테스트는 H2 | MariaDB |
| 쿠키 | http | https 라면 `Secure` 필요 |

"비밀번호 변경 필요" 필터가 모든 요청을 검사했기 때문에, 운영에서는 화면 파일까지 막혀 비밀번호 변경 화면을 띄울 수 없었습니다.
교훈: **운영과 같은 구성으로 한 번은 끝까지 띄워 봐야 한다.** 테스트가 아무리 많아도 구성이 다르면 놓치는 것이 있습니다.

**OncePerRequestFilter.shouldNotFilter()**: 필터가 처리할 필요 없는 요청을 건너뛰는 방법입니다. 여기서는 `/api/` 가 아니면 건너뜁니다.
덕분에 세션 동기화 필터가 화면 파일 요청마다 DB 를 조회하던 낭비도 함께 없어졌습니다.

**SPA fallback**: Vue 라우터 주소(`/projects/3`)는 실제 파일이 아니라서, 새로고침하면 서버가 404 를 줍니다.
서버가 "없는 파일이면 index.html" 을 주도록 해야 합니다. 단, `/api` 주소까지 index.html 로 바꾸면 없는 API 가 200 HTML 로 응답되는 이상한 일이 생기므로 제외합니다.
> 코드: `config/web/FrontendConfig.java`

**환경 변수로 운영 설정 분리**: 같은 jar 를 개발·운영에서 쓰고, 차이(DB 주소, 저장 위치, HTTPS 쿠키, 관리자 비밀번호)는 환경 변수로만 바꿉니다. 코드나 설정 파일을 고쳐서 배포하지 않습니다.
> 문서: `docs/operations.md`

---

## 24. 성능을 숫자로 확인하기

"느리지 않을 것 같다"가 아니라 측정해서 확인했습니다.

**메모리: 힙을 일부러 작게 잡고 큰 파일 보내기**
`java -Xmx256m`(힙 최대 256MB)으로 띄우고 490MB 파일을 올리고 받았습니다.
파일을 `byte[]`로 통째로 읽는 코드였다면 `OutOfMemoryError` 로 죽었을 것입니다. 스트리밍(13번) 덕분에 프로세스 메모리는 약 300MB 에서 멈췄습니다.

**SQL 수: 요청 하나에 쿼리가 몇 번 나가나**
`logging.level.org.hibernate.SQL=DEBUG` 로 실행하면 Hibernate 가 실행하는 SQL 이 로그에 찍힙니다.
요청 전후 로그 줄 수를 세어 보니 모든 API 가 2~3회였고, 목록이 길어져도 늘지 않았습니다 (N+1 없음, 5번).
그중 1회는 세션 동기화 필터(20번)의 사용자 조회입니다 — "기능을 위해 감수한 비용"이 실제로 얼마인지도 숫자로 확인한 셈입니다.

| 확인 방법 | 도구 |
|---|---|
| 메모리 | `-Xmx` 로 힙 제한, `ps` / 작업 관리자로 프로세스 메모리 |
| SQL 수 | `org.hibernate.SQL=DEBUG` 로그 |
| 응답 시간 | `curl -w '%{time_total}'`, 브라우저 개발자 도구 Network 탭 |
