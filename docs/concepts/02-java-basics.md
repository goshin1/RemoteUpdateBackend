# 2부. 스프링 전에 다지는 자바

[← 1부](01-web-basics.md) · [목차](../backend-concepts.md) · [3부 →](03-spring-core.md)

스프링 코드를 처음 보면 `@` 투성이에, 화살표(`->`)와 `::` 가 섞여 있어 낯섭니다.
스프링이 마법처럼 보이는 이유의 절반은 **스프링이 아니라 자바 문법**을 모르기 때문입니다. 이 부에서는 스프링 코드를 읽기 위해 꼭 필요한 자바만 골라 봅니다.

---

## 5장. 어노테이션 — 코드에 붙이는 메모

### 이야기

`@Service`, `@Transactional`, `@NotBlank` ... 이 `@` 들은 무슨 일을 할까요?
놀랍게도 **어노테이션 자체는 아무 일도 하지 않습니다.** 그냥 코드에 붙은 **메모(메타데이터)** 입니다. 일을 하는 것은 그 메모를 **읽는 쪽**입니다.

### 개념

```java
@Service                       // 메모: "이 클래스는 서비스야"
public class ProjectService {

    @Transactional             // 메모: "이 메서드는 트랜잭션 안에서 실행해 줘"
    public ProjectResponse create(ProjectRequest request) { ... }
}
```

- 자바는 실행 중에 "이 클래스에 어떤 어노테이션이 붙어 있지?" 를 **물어볼 수 있습니다**. 이것을 **리플렉션(reflection)** 이라고 합니다.
- 스프링은 앱을 시작할 때 클래스들을 훑어보며(**컴포넌트 스캔**) `@Service` 메모가 붙은 클래스를 찾아 객체를 만들고, `@Transactional` 메모가 붙은 메서드는 트랜잭션으로 감쌉니다 (3부 12~13장).
- `@NotBlank` 는 Bean Validation 라이브러리가 읽고, `@Entity` 는 JPA(Hibernate) 가 읽습니다.

어노테이션은 **누가 읽느냐**에 따라 세 종류로 나눠 보면 이해가 쉽습니다.

| 읽는 시점 | 누가 | 예 |
|---|---|---|
| **컴파일할 때** (코드를 만들어 냄) | Lombok, 컴파일러 | `@Getter`, `@RequiredArgsConstructor`, `@Override` |
| **앱 시작할 때** | 스프링, JPA | `@Service`, `@Entity`, `@ConfigurationProperties` |
| **메서드 호출할 때마다** | 스프링 프록시, 검증기 | `@Transactional`, `@PreAuthorize`, `@Valid` |

### 그래서 생기는 함정

"메모를 읽는 쪽이 일을 한다" 는 걸 알면 이런 버그가 이해됩니다.
- 어노테이션을 붙였는데 **읽는 쪽이 꺼져 있으면** 아무 일도 안 일어납니다. 예: `@CreatedDate` 는 `@EnableJpaAuditing`(우리는 `JpaConfig`)이 있어야 동작합니다.
- 읽는 쪽이 **볼 수 없는 경로**로 호출하면 무시됩니다. 예: 같은 클래스 안에서 `@Transactional` 메서드를 부르면 트랜잭션이 안 걸립니다 (3부 13장).

### 정리

- 어노테이션은 메모일 뿐, 일은 그것을 읽는 프레임워크가 한다.
- 읽는 시점이 컴파일 / 시작 / 호출 중 언제인지 구분하면 동작을 예측할 수 있다.

### 확인 문제

5-1. `@ConfigurationProperties` 를 붙인 클래스가 있는데 값이 안 들어옵니다. 우리 앱에서 이 메모를 "읽게 만드는" 어노테이션은 어디에 있을까요? (힌트: `RemoteUpdateBackendApplication`)

---

## 6장. 인터페이스와 다형성 — 구현을 숨기는 기술

### 이야기

기획서에 "나중에 파일을 클라우드(S3 등)에 저장할 수도 있다" 고 적혀 있었습니다.
지금 `UpdateService` 가 `Files.write(...)` 로 디스크에 직접 쓰고 있다면, 클라우드로 옮길 때 파일을 다루는 **모든 서비스를 고쳐야** 합니다.

### 개념: "무엇을 하는지" 와 "어떻게 하는지" 를 나눈다

```java
// 무엇을: 저장하고, 읽고, 지운다 (약속만)
public interface FileStorage {
    StoredFile store(FileCategory category, String originalName, InputStream content);
    Resource load(String key);
    void delete(String key);
}

// 어떻게: 로컬 디스크에 (지금)
@Component
public class LocalFileStorage implements FileStorage { ... }

// 어떻게: S3 에 (나중에 이 클래스만 추가)
// public class S3FileStorage implements FileStorage { ... }
```

서비스는 **인터페이스 타입**으로만 받습니다.

```java
public class UpdateService {
    private final FileStorage fileStorage;   // LocalFileStorage 인지 S3 인지 모름
    ...
    stored = fileStorage.store(FileCategory.UPDATES, fileName, in);
}
```

같은 `fileStorage.store(...)` 호출이 실제 객체에 따라 다르게 동작하는 것을 **다형성(polymorphism)** 이라고 합니다.
구현을 바꿔도 `UpdateService`, `GuideService` 는 한 줄도 바뀌지 않습니다.

### 우리가 쓰는 라이브러리도 전부 이 방식

- `PasswordEncoder` 인터페이스 ← `BCryptPasswordEncoder` 구현 (`SecurityConfig.passwordEncoder()`)
- `JpaRepository` 인터페이스 ← 스프링 데이터가 실행 중에 구현을 만들어 줌 (4부 25장)
- `Resource` 인터페이스 ← `FileSystemResource` 구현 (다운로드)

> 덕분에 테스트에서 가짜 구현으로 바꿔 끼우기도 쉽습니다 (7부).

### 정리

- 인터페이스는 약속, 구현 클래스는 실제 방법.
- 사용하는 쪽은 인터페이스만 알면 구현을 갈아 끼울 수 있다.

### 확인 문제

6-1. `UpdateService` 의 필드를 `private final LocalFileStorage fileStorage;` 로 바꾸면 무엇이 불편해질까요?

---

## 7장. 람다, Optional, Stream

### 이야기

우리 코드에는 이런 줄이 자주 나옵니다.

```java
return projectRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.PROJECT_NOT_FOUND));
return projectRepository.findAll(Sort.by("name")).stream().map(ProjectResponse::from).toList();
```

`->` 와 `::` 를 읽을 수 있으면 스프링 코드의 절반이 쉬워집니다.

### 람다: "나중에 실행할 코드 조각"

```java
() -> new ApiException(ErrorCode.PROJECT_NOT_FOUND)
```

"파라미터 없음(`()`), 실행하면(`->`) 예외 객체를 만든다" 는 **코드 조각**입니다. 지금 실행되지 않고, 받는 쪽이 **필요할 때** 실행합니다.
위 `orElseThrow` 는 값이 없을 때만 이 조각을 실행합니다. 값이 있으면 예외 객체를 아예 만들지 않습니다.

**메서드 참조 `::`** 는 람다를 더 짧게 쓴 것입니다.

```java
.map(project -> ProjectResponse.from(project))   // 람다
.map(ProjectResponse::from)                      // 같은 뜻, 메서드 참조
```

람다를 받는 자리의 타입을 **함수형 인터페이스**라고 합니다 (메서드가 딱 하나인 인터페이스). 우리 코드의 대표 예가 **Specification** 입니다.

```java
static Specification<DownloadHistory> userId(Long userId) {
    return (root, query, cb) -> userId == null ? null : cb.equal(root.get("user").get("id"), userId);
}
```

"검색 조건을 만드는 방법" 을 람다로 돌려주고, 실제 SQL 을 만들 때 JPA 가 실행합니다 (4부 25장).

### Optional: "없을 수도 있음" 을 타입으로 표현

`findById` 는 `AppUser` 가 아니라 `Optional<AppUser>` 를 돌려줍니다. "없을 수도 있으니 처리해" 라는 뜻입니다.

| 쓰는 법 | 의미 | 우리 코드 |
|---|---|---|
| `.orElseThrow(() -> ...)` | 없으면 예외 | 대부분의 `getXxx()` 메서드 |
| `.orElse(null)` | 없으면 null | `AuthService.authenticate()` — 없는 사용자일 때도 비밀번호 비교를 한 번 하려고 (5부 28장) |
| `.isPresent()` / `.map(...)` | 있을 때만 처리 | `SessionUserRefreshFilter` |

`null` 을 그냥 돌려주면 받는 쪽이 검사를 잊기 쉽고, 잊으면 `NullPointerException` 이 납니다. Optional 은 **잊지 못하게** 만듭니다.

### Stream: 목록을 "변환 파이프라인" 으로

```java
projectRepository.findAll(Sort.by("name"))   // List<Project>
        .stream()                            // 흘려보내기 시작
        .map(ProjectResponse::from)          // 하나씩 DTO 로 변환
        .toList();                           // 다시 List 로 모음
```

for 문으로 쓰면 "빈 리스트 만들기 → 반복 → add" 를 직접 써야 하지만, Stream 은 **무엇을 할지**만 씁니다. 자주 쓰는 것: `map`(변환), `filter`(거르기), `sorted`(정렬), `findFirst`(첫 번째), `toList`(모으기).

> 예: `GlobalExceptionHandler.handleMethodArgumentNotValid()` — 검증 오류 중 첫 번째 메시지를 `stream().findFirst().map(...).orElse(...)` 로 꺼냄

### 정리

- 람다 `->` 는 나중에 실행할 코드 조각, `::` 는 그 짧은 표기.
- Optional 은 "없을 수도 있음" 을 강제로 처리하게 하는 상자.
- Stream 은 목록 변환을 map/filter 파이프라인으로 쓰는 방법.

### 확인 문제

7-1. `orElseThrow(new ApiException(...))` 처럼 람다 없이 쓸 수 없는 이유는 무엇일까요? (힌트: 값이 있을 때도 예외 객체가 만들어지면?)

---

## 8장. 예외 — checked 와 unchecked

### 이야기

같은 버전을 또 등록하면 서비스는 "안 돼" 라고 말해야 합니다. 이 "안 돼" 를 어떻게 전달할까요?
메서드가 `null` 이나 `-1` 같은 특별한 값을 돌려주는 방법도 있지만, 호출한 쪽이 검사를 잊으면 그대로 진행돼 버립니다. **예외(exception)** 는 검사를 잊어도 **흐름 자체를 멈춥니다**.

### 개념: 두 종류의 예외

| | checked 예외 | unchecked 예외 |
|---|---|---|
| 부모 클래스 | `Exception` | `RuntimeException` |
| 컴파일러 강제 | `throws` 선언 또는 `try-catch` **필수** | 강제 없음 |
| 예 | `IOException`(파일), `NoSuchAlgorithmException` | `NullPointerException`, `IllegalArgumentException`, **우리 `ApiException`** |
| `@Transactional` 기본 동작 | 롤백 **안 함** | 롤백 **함** (4부 20장) |

우리는 업무 규칙 위반을 전부 `ApiException`(unchecked) 하나로 표현합니다.

```java
public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;    // 어떤 오류인지 (HTTP 상태 + 메시지가 함께 정해짐)
    ...
}

throw new ApiException(ErrorCode.DUPLICATE_VERSION);   // 서비스 어디서든
```

- unchecked 라서 중간 메서드들이 `throws` 를 줄줄이 달 필요가 없고,
- 트랜잭션이 자동으로 롤백되며,
- 맨 바깥의 `GlobalExceptionHandler` 가 받아서 `{ code, message }` 응답으로 바꿉니다 (3부 17장).

### checked 예외는 경계에서 바꾼다

파일 저장처럼 `IOException` 이 나는 곳에서는 잡아서 우리 예외로 **바꿔 던집니다**.

```java
try (InputStream in = file.getInputStream()) {
    stored = fileStorage.store(FileCategory.UPDATES, fileName, in);
} catch (IOException e) {
    throw new ApiException(ErrorCode.FILE_STORAGE_ERROR);
}
```

### 예외를 다룰 때의 습관

- **삼키지 않는다.** `catch (Exception e) { }` 처럼 비워 두면 문제가 조용히 사라집니다. 최소한 로그를 남깁니다. (예외: `LocalFileStorage` 에서 임시 파일 삭제 실패처럼 "실패해도 상관없는" 정리 작업)
- **원인을 함께 남긴다.** 로그에 `log.error("파일 저장 실패: key={}", key, e)` 처럼 예외 객체 `e` 를 넘기면 스택(어디서 났는지)이 기록됩니다.
- **사용자에게는 내부 사정을 숨긴다.** 응답 메시지에 SQL 이나 파일 경로를 넣지 않습니다.

### 정리

- checked 는 처리 강제, unchecked 는 강제 없음. 트랜잭션은 기본적으로 unchecked 에서만 롤백한다.
- 업무 오류는 `ApiException(ErrorCode)` 하나로 던지고, 맨 바깥에서 한 번에 응답으로 바꾼다.

### 확인 문제

8-1. 서비스 메서드에서 `IOException` 을 잡지 않고 `throws IOException` 으로 밖에 던지면, `@Transactional` 메서드의 DB 작업은 어떻게 될까요?

---

## 9장. record, enum, Lombok

### record — 값을 담는 상자 (Java 16+)

```java
public record StoredFile(String key, String originalName, long size, String sha256) { }
```

이 한 줄이 생성자, 값 꺼내기(`stored.key()`), `equals`, `hashCode`, `toString` 을 자동으로 만듭니다. 필드는 전부 `final` 이라 **만든 뒤 바꿀 수 없습니다(불변)**.
값을 옮기기만 하는 DTO 에 딱 맞아 우리 요청·응답 클래스는 거의 다 record 입니다.

- `equals` 가 모든 필드를 비교한다는 점을 실제로 이용한 곳: `SessionUserRefreshFilter` 가 세션의 사용자 정보(`UserPrincipal` record)와 DB 에서 새로 만든 값을 `equals` 로 비교해 **바뀐 게 있을 때만** 세션을 갱신합니다 (5부 30장).

### enum — 정해진 값만 허용하는 타입

```java
public enum UpdateStatus { ACTIVE, DISABLED }
public enum Role { STAFF, DEVELOPER, ADMIN; ... }
```

상태를 `String` 으로 두면 `"Active"`, `"actived"` 같은 오타가 들어갈 수 있지만, enum 은 **컴파일러가 막아 줍니다**.
enum 도 클래스라서 메서드를 가질 수 있습니다. `ErrorCode` 는 각 값마다 HTTP 상태와 메시지를 함께 들고 있는 enum 입니다.

> 주의: `Role.includes()` 는 선언 순서(`ordinal()`)로 위아래를 비교합니다. 그래서 **선언 순서를 바꾸면 권한이 뒤집힙니다.** 반대로 DB 에는 순서가 아닌 **이름**으로 저장합니다 (`@Enumerated(EnumType.STRING)`, 4부 24장).

### Lombok — 반복 코드를 컴파일할 때 만들어 주는 도구

| 어노테이션 | 만들어 주는 것 |
|---|---|
| `@Getter` | 모든 필드의 `getXxx()` |
| `@RequiredArgsConstructor` | `final` 필드를 받는 생성자 → 생성자 주입에 사용 (3부 12장) |
| `@NoArgsConstructor(access = PROTECTED)` | 파라미터 없는 생성자 (JPA 용, 4부 24장) |
| `@Slf4j` | `log` 변수 (`log.info(...)`) |

Lombok 은 **컴파일할 때** 코드를 만들어 넣습니다(5장의 "컴파일할 때 읽는 어노테이션"). 그래서 IDE 에 Lombok 플러그인이 없으면 "getter 가 없다" 는 빨간 줄이 생깁니다.
우리는 **엔티티에 `@Setter` 를 쓰지 않습니다.** 아무 데서나 값을 바꿀 수 있으면 규칙이 흩어지기 때문입니다 (4부 24장).

### 정리

- record 는 불변 값 상자, enum 은 정해진 값만 허용하는 타입.
- Lombok 은 컴파일 때 getter·생성자·로거를 만들어 준다. setter 는 일부러 안 쓴다.

### 확인 문제

9-1. `Role` enum 에 `MANAGER` 를 `STAFF` 와 `DEVELOPER` 사이에 추가하면 `includes()` 는 어떻게 동작할까요? DB 에 저장된 기존 사용자 역할은 영향을 받을까요?

---

## 10장. 스레드와 동시성 — 요청은 동시에 온다

### 이야기

직원 10명이 동시에 다운로드 버튼을 누르면, 서버는 한 명씩 차례로 처리할까요?
아닙니다. 웹 서버(톰캣)는 요청이 올 때마다 **스레드(thread)** 하나를 배정해 **동시에** 처리합니다(기본 최대 200개). 동시에 실행되는 코드는 혼자 실행될 때는 없던 문제를 만듭니다.

### 개념 1: 공유하는 것과 공유하지 않는 것

| 공유하지 않음 (스레드마다 따로) | 모든 스레드가 공유 |
|---|---|
| 메서드 안의 지역 변수, 파라미터 | **스프링 빈의 필드** (빈은 앱 전체에 하나뿐 — 3부 12장) |
| | DB 의 데이터 |

그래서 **빈에 요청마다 바뀌는 값을 필드로 두면 안 됩니다.**

```java
@Service
public class BadService {
    private Long currentUserId;              // ❌ 모든 요청이 같은 칸을 씀
    public void handle(Long userId) {
        this.currentUserId = userId;         // A 요청이 넣고
        ...                                  // 그 사이 B 요청이 덮어씀
        save(currentUserId);                 // A 요청이 B 의 ID 로 저장!
    }
}
```

우리 서비스들의 필드는 전부 `private final` 로 다른 빈(Repository 등)만 들고 있습니다. **바뀌지 않는 것만 필드로** 두는 것이 규칙입니다.

### 개념 2: 경쟁 조건(race condition)

**두 작업의 순서가 그때그때 달라서** 결과가 달라지는 버그입니다. 재현이 잘 안 돼서 찾기 어렵습니다.

**Backend 의 예 — 같은 버전을 두 사람이 동시에 등록**

```
시간 →   개발자 A                         개발자 B
         existsBy(1.2.0)? → 없음
                                           existsBy(1.2.0)? → 없음
         저장(1.2.0)
                                           저장(1.2.0)   → 두 개가 생김?!
```

코드의 "먼저 확인" 만으로는 막을 수 없습니다. 그래서 **DB 유니크 제약**(`project_id + version`)이 최종 방어선입니다. 두 번째 저장은 DB 가 거부하고, 우리는 그 오류를 409 로 바꿉니다.

```java
try {
    info = updateRepository.saveAndFlush(...);     // 즉시 SQL 실행 → 제약 위반을 이 자리에서 잡음
} catch (DataIntegrityViolationException e) {
    throw new ApiException(ErrorCode.DUPLICATE_VERSION);
}
```

> `existsBy...` 검사는 그래도 둡니다. 대부분의 경우 친절한 메시지를 빨리 주고, 드문 동시 상황만 DB 가 막습니다.

**Frontend 의 예 — 실제로 만난 버그**
업로드 화면을 열면 서버에서 업로드 정책(허용 확장자)을 받아오는데, 그 응답이 오기 **전에** 파일을 고르면 확장자 검사를 건너뛰었습니다. E2E 테스트(7부)에서 페이지를 열자마자 파일을 고르는 순서일 때만 실패해서 발견했습니다. 해결: 검사 전에 정책 조회가 끝나기를 기다림 (`useUploadPolicy().ready()`).
(서버가 최종 검사를 하므로 보안 문제는 아니었고, 안내가 늦게 나오는 문제였습니다.)

### 정리

- 요청마다 스레드가 따로 돌고, 빈의 필드와 DB 는 모두가 공유한다.
- 빈에는 바뀌지 않는 것만 필드로 둔다.
- "확인 후 저장" 은 동시에 실행되면 깨진다 → DB 제약이 최종 방어선.

### 확인 문제

10-1. 업데이트 다운로드 횟수를 `update_info.download_count` 컬럼에 `읽기 → +1 → 저장` 으로 늘린다면, 동시에 두 명이 받을 때 어떤 문제가 생길 수 있을까요? (우리는 이 대신 다운로드마다 **이력 행을 하나씩 추가**합니다.)

---

[← 1부](01-web-basics.md) · [목차](../backend-concepts.md) · [3부. 스프링 핵심 →](03-spring-core.md)
