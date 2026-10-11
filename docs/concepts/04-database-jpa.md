# 4부. 데이터베이스와 JPA

[← 3부](03-spring-core.md) · [목차](../backend-concepts.md) · [5부 →](05-security.md)

서버는 언제든 꺼졌다 켜질 수 있습니다. 메모리에 있던 것은 사라집니다. 그래서 **사라지면 안 되는 것**(사용자, 업데이트 정보, 다운로드 기록)은 데이터베이스에 둡니다.
이 부는 DB 자체의 기초(18~21장)부터 시작해, 그 위에서 JPA 가 무엇을 대신 해 주는지(22~26장)로 올라갑니다. **JPA 를 잘 쓰려면 결국 DB 와 SQL 을 알아야 합니다.**

---

## 18장. 관계형 데이터베이스 기초

### 이야기

엑셀 시트 하나에 "업데이트 버전, 제목, 프로젝트 이름, 프로젝트 설명, 개발자 이름, 개발자 이메일" 을 다 적는다고 해 봅시다.
프로젝트 설명을 고치려면 그 프로젝트의 **모든 행**을 고쳐야 하고, 한 줄이라도 빼먹으면 데이터가 어긋납니다. 관계형 DB 는 **"하나의 사실은 한 곳에만"** 적고, 나머지는 **번호로 가리키게** 해서 이 문제를 풉니다.

### 개념: 테이블, 행, 키

```
project                         update_info
┌────┬──────────────────┐       ┌────┬────────────┬─────────┬───────────────┬──────────────┐
│ id │ name             │       │ id │ project_id │ version │ title         │ developer_id │
├────┼──────────────────┤       ├────┼────────────┼─────────┼───────────────┼──────────────┤
│  1 │ 스마트 관제 시스템 │◀──────│ 10 │     1      │ 1.0.0   │ 최초 배포      │      2       │
│  2 │ 출입 관리 시스템  │       │ 11 │     1      │ 1.1.0   │ 알림 기능 추가 │      2       │
└────┴──────────────────┘       └────┴────────────┴─────────┴───────────────┴──────────────┘
```

| 용어 | 뜻 | 우리 예 |
|---|---|---|
| **테이블** | 같은 종류의 데이터 묶음 | `project`, `update_info`, `app_user` |
| **행(row)** / **열(column)** | 데이터 한 건 / 항목 하나 | 업데이트 1.1.0 한 건 / `title` |
| **기본키(PK)** | 행을 구분하는 유일한 번호 | 모든 테이블의 `id` (DB 가 자동 증가) |
| **외래키(FK)** | 다른 테이블의 행을 가리키는 번호 | `update_info.project_id` → `project.id` |
| **유니크 제약** | 같은 값이 두 번 못 들어감 | `app_user.email`, `project.name`, `(project_id, version)` 묶음 |
| **NOT NULL** | 비워 둘 수 없음 | `update_info.title` |

외래키는 **존재하지 않는 프로젝트를 가리키는 업데이트**가 생기지 못하게 DB 가 막아 줍니다. 유니크 제약은 2부 10장에서 본 것처럼 **동시 요청의 최종 방어선**입니다.

### 우리 테이블 지도 (ERD)

```
app_user ◀─── developer_id ─── update_info ───── project_id ───▶ project
   ▲  ▲                          ▲     ▲                           ▲
   │  └─ changed_by ─ update_history ┘  │                           │
   │                                    │                           │
   ├──── user_id ── download_history ── update_id                   │
   ├──── created_by ── setup_guide ─────────────── project_id ──────┘
   └──── created_by ── allowed_ip
```

전체 정의: `docs/schema-mariadb.sql`

### 인덱스 — 책의 찾아보기

"3번 프로젝트의 배포 중인 업데이트를 최신순으로" 를 찾을 때, 인덱스가 없으면 DB 는 **모든 행을 처음부터 끝까지** 읽습니다(풀 스캔). 인덱스는 특정 열 기준으로 정렬해 둔 "찾아보기" 라서 필요한 행으로 바로 갑니다.

```java
@Table(name = "update_info",
        indexes = @Index(name = "idx_update_info_project_status_created",
                columnList = "project_id, status, created_at"))     // 자주 쓰는 조회 조건 순서대로
```

- **자주 검색·정렬하는 열**에 겁니다. 우리는 목록 조회 조건(프로젝트 + 상태 + 등록일), 다운로드 이력의 기간·사용자 검색에 걸었습니다.
- 공짜가 아닙니다. 행을 추가·수정할 때 인덱스도 고쳐야 하므로 **쓰기가 조금 느려지고** 공간을 씁니다.
- 기본키와 유니크 제약에는 DB 가 인덱스를 자동으로 만듭니다.

### 정리

- 하나의 사실은 한 테이블에, 관계는 외래키(번호)로.
- 제약(PK, FK, UNIQUE, NOT NULL)은 DB 가 지켜 주는 규칙 — 코드 실수의 마지막 안전망.
- 인덱스는 읽기를 빠르게, 쓰기를 조금 느리게.

### 확인 문제

18-1. `update_info` 에 `project_name` 열을 따로 두면 생기는 문제는 무엇일까요?
18-2. 유니크 제약이 `version` 하나에만 걸려 있다면 어떤 문제가 생길까요?

---

## 19장. SQL 맛보기 — JPA 가 대신 써 주는 것

### 이야기

우리 코드에는 SQL 이 거의 없습니다. JPA 가 만들어 주기 때문입니다. 그래도 **JPA 가 어떤 SQL 을 만드는지 읽을 수 있어야** 성능 문제(23장 N+1)를 알아채고 고칠 수 있습니다.

### 네 가지 기본 문장

```sql
-- 조회: 3번 프로젝트의 배포 중인 업데이트, 최신순 20개
SELECT id, version, title, created_at
FROM update_info
WHERE project_id = 3 AND status = 'ACTIVE'
ORDER BY created_at DESC, id DESC
LIMIT 20 OFFSET 0;

-- 추가
INSERT INTO project (name, description, created_at, updated_at) VALUES ('스마트 관제', '...', NOW(), NOW());

-- 수정
UPDATE update_info SET title = '알림 기능 추가', updated_at = NOW() WHERE id = 11;

-- 삭제 (우리는 거의 안 씀 — 중단/중지로 대신)
DELETE FROM allowed_ip WHERE id = 5;
```

### JOIN — 여러 테이블을 붙여서 읽기

업데이트 목록에 **개발자 이름**도 보여 줘야 합니다. 이름은 `app_user` 에 있으니 붙여서 읽습니다.

```sql
SELECT u.version, u.title, d.name AS developer_name
FROM update_info u
JOIN app_user d ON d.id = u.developer_id     -- 외래키로 연결
WHERE u.project_id = 3;
```

### JPA 가 만든 SQL 직접 보기

`application.yml` 에서 `logging.level.org.hibernate.SQL: DEBUG` 로 바꾸면 실행되는 SQL 이 로그에 찍힙니다. 공부할 때 꼭 켜 보세요. 우리 설정에는 `format_sql: true` 가 있어 보기 좋게 줄바꿈됩니다.

> 8부 41장에서 이 로그로 **요청당 SQL 수**를 세어 성능을 확인합니다.

### 정리

- SELECT / INSERT / UPDATE / DELETE, 그리고 JOIN 만 읽을 줄 알아도 JPA 로그를 이해할 수 있다.
- SQL 로그를 켜고 "내 코드가 어떤 SQL 을 몇 번 만드는지" 보는 습관을 들이자.

### 확인 문제

19-1. 위 조회 SQL 에서 `ORDER BY created_at DESC` 만 쓰고 `id DESC` 를 빼면, 등록 시각이 같은 행이 있을 때 페이지를 넘기며 어떤 일이 생길 수 있을까요? (25장에서 확인)

---

## 20장. 트랜잭션 — 전부 아니면 전무

### 이야기

업데이트 등록은 DB 에 두 가지를 씁니다: `update_info`(업데이트 정보) + `update_history`(등록 이력).
첫 번째는 들어갔는데 두 번째에서 오류가 나면? 이력 없는 업데이트가 생겨 기록이 어긋납니다. **둘 다 들어가거나, 둘 다 안 들어가야** 합니다.

### 개념: ACID

트랜잭션은 여러 DB 작업을 하나로 묶는 단위로, 네 가지를 보장합니다.

| 성질 | 뜻 | 예 |
|---|---|---|
| **A**tomicity 원자성 | 전부 성공 or 전부 취소(**롤백**) | 정보 + 이력이 같이 저장되거나 같이 취소 |
| **C**onsistency 일관성 | 제약(유니크, 외래키)을 깨는 상태로 끝나지 않음 | 같은 버전 두 개는 커밋되지 않음 |
| **I**solation 격리성 | 동시에 실행되는 트랜잭션끼리 중간 상태를 보지 않음 | A 가 저장 중인 업데이트를 B 가 반쯤 보지 않음 |
| **D**urability 지속성 | 커밋된 것은 서버가 꺼져도 남음 | 디스크에 기록 |

> 격리성은 수준(isolation level)을 고를 수 있습니다. MariaDB(InnoDB) 기본값은 `REPEATABLE READ` 로, 한 트랜잭션 안에서 같은 행을 여러 번 읽으면 같은 값이 보입니다. 우리는 기본값을 그대로 씁니다. 수준을 높일수록 안전하지만 느려지고, 낮출수록 빠르지만 이상한 값이 보일 수 있습니다 (부록 "다음에 공부할 것").

### 스프링에서: @Transactional

```java
@Transactional
public UpdateResponse create(...) {
    updateRepository.saveAndFlush(info);       // 1
    historyRepository.save(history);           // 2  ← 여기서 예외가 나면 1도 취소(롤백)
}
```

3부 13장의 프록시가 메서드 시작 때 트랜잭션을 열고, 정상 종료면 **커밋**, 예외가 나가면 **롤백**합니다.

**알아둘 규칙**

| 규칙 | 설명 |
|---|---|
| RuntimeException 이면 롤백 | 체크 예외(IOException 등)는 기본적으로 롤백하지 않음 (2부 8장). 우리는 `ApiException`(RuntimeException)을 씀 |
| `readOnly = true` | 조회 전용. 변경 감지(22장)를 건너뛰어 조금 빠르고, 실수로 수정하는 것을 막음. **클래스에 기본으로 걸고 쓰기 메서드에만 `@Transactional` 을 다시 붙임** |
| `noRollbackFor` | 예외가 나도 커밋. **로그인 실패 횟수 기록**에 사용 (예외를 던지지만 실패 횟수는 저장돼야 함) |
| 같은 클래스 안 호출 | 프록시를 거치지 않아 적용 안 됨 (3부 13장) |

```java
@Service
@Transactional(readOnly = true)              // 기본: 읽기 전용
public class ProjectService {
    public List<ProjectResponse> findAll() { ... }    // 읽기 전용 트랜잭션

    @Transactional                           // 쓰기 메서드만 다시 지정
    public ProjectResponse create(...) { ... }
}
```

```java
@Transactional(noRollbackFor = ApiException.class)
public UserPrincipal authenticate(LoginRequest request) {
    ...
    if (비밀번호 틀림) {
        user.recordLoginFailure(...);                    // 실패 횟수 +1 (22장의 더티 체킹으로 저장)
        throw new ApiException(ErrorCode.INVALID_CREDENTIALS);   // 예외지만 롤백하지 않음 → 횟수는 커밋됨
    }
}
```

**트랜잭션은 짧게.** 트랜잭션이 열려 있는 동안 DB 연결(21장)을 붙잡습니다. 그래서 몇 분 걸리는 파일 전송은 **트랜잭션 밖에서** 합니다 (6부 32장).

> 코드: `UpdateService`(클래스 `readOnly` + 메서드별 쓰기), `AuthService.authenticate()`(`noRollbackFor`)

### 정리

- 트랜잭션 = 전부 아니면 전무. 스프링에서는 `@Transactional` 프록시가 커밋/롤백한다.
- 클래스에 `readOnly = true`, 쓰기 메서드만 `@Transactional`.
- 트랜잭션은 짧게 — DB 연결을 붙잡고 있기 때문.

### 확인 문제

20-1. `noRollbackFor` 없이 로그인 실패를 처리하면, 비밀번호를 100번 틀려도 계정이 잠기지 않습니다. 왜일까요?

---

## 21장. 커넥션 풀

### 이야기

DB 에 연결하는 일은 비쌉니다 (네트워크 연결, 인증, 준비). 요청마다 연결하고 끊으면 그 시간이 계속 쌓입니다.

### 개념

**커넥션 풀**은 DB 연결을 **미리 여러 개 만들어 두고 빌려 쓰고 돌려주는** 방식입니다. 스프링 부트는 **HikariCP** 를 기본으로 씁니다(기본 최대 10개).

```
요청 스레드 1 ──빌림──▶ [연결1] ─┐
요청 스레드 2 ──빌림──▶ [연결2]  ├─ 풀 (최대 10개) ── MariaDB
요청 스레드 3 ──대기...          ┘   ← 10개 모두 사용 중이면 반납될 때까지 기다림
```

- 트랜잭션이 시작될 때 빌리고, 끝날 때 반납합니다.
- 톰캣 스레드는 200개인데 연결은 10개입니다. 그래서 **트랜잭션을 오래 잡으면** 나머지 요청이 줄줄이 기다리게 됩니다. 20장의 "트랜잭션은 짧게", 22장 `open-in-view: false`, 6부의 "다운로드는 트랜잭션 밖에서" 가 전부 이 이유입니다.

### 정리

- DB 연결은 풀에서 빌려 쓰고 돌려준다 (HikariCP, 기본 10개).
- 연결은 스레드보다 훨씬 적다 → 연결을 오래 쥐고 있는 코드가 전체를 느리게 만든다.

---

## 22장. JPA 와 영속성 컨텍스트 — 더티 체킹의 비밀

### 이야기

자바는 **객체**로, DB 는 **테이블**로 생각합니다. 매번 `ResultSet` 에서 열을 하나씩 꺼내 객체에 넣고, 객체 값을 꺼내 `INSERT` 문을 만드는 코드는 지루하고 실수하기 쉽습니다.
**ORM**(Object-Relational Mapping)은 이 변환을 자동으로 해 줍니다. 자바 표준 ORM 규격이 **JPA**, 그 구현체가 **Hibernate** 입니다.

### 개념 1: 엔티티 = 테이블과 연결된 클래스

```java
@Entity                                     // JPA 가 관리하는 클래스
@Table(name = "update_info")               // 연결될 테이블
public class UpdateInfo extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)   // 기본키, DB 자동 증가
    private Long id;

    @Column(nullable = false, length = 200)   // NOT NULL, VARCHAR(200)
    private String title;
    ...
}
```

### 개념 2: 영속성 컨텍스트 — JPA 의 작업대

트랜잭션 동안 JPA 는 다룬 엔티티들을 **작업대(영속성 컨텍스트)** 에 올려 두고 추적합니다. 이 작업대가 JPA 의 거의 모든 "마법" 의 정체입니다.

```
트랜잭션 시작
  │  findById(11)  → SELECT 실행 → 엔티티를 작업대에 올리고 "원래 값 사진(스냅숏)" 도 찍어 둠
  │  findById(11)  → 작업대에 이미 있음 → SQL 없이 같은 객체 반환 (1차 캐시)
  │  info.updateMeta("새 제목", ...)  → 객체 값만 바뀜 (아직 SQL 없음)
  │
트랜잭션 커밋 직전: flush
  │  작업대의 엔티티들을 사진과 비교 → 바뀐 것 발견 → UPDATE 실행 (더티 체킹)
  ▼
커밋 → 작업대 비움
```

### 개념 3: 더티 체킹 — save 를 안 불러도 저장되는 이유

그래서 우리 수정 코드에는 `save()` 가 없습니다.

```java
@Transactional
public UpdateResponse updateMeta(Long updateId, UpdateMetaRequest request, UserPrincipal editor) {
    UpdateInfo info = getUpdate(updateId);                     // 조회 → 작업대에 올라감
    info.updateMeta(request.title().trim(), request.content()); // 값만 바꿈
    return UpdateResponse.from(info);
}   // ← 트랜잭션 종료 시 바뀐 부분을 찾아 UPDATE 자동 실행
```

**주의**
- 트랜잭션 밖에서 바꾸면 작업대가 없으니 반영되지 않습니다.
- `readOnly = true` 트랜잭션에서는 사진을 찍지 않아 바꿔도 저장되지 않습니다 (그래서 빠름).
- **새** 엔티티(아직 DB 에 없는 것)는 작업대에 올리기 위해 `save()` 가 필요합니다.

### 엔티티의 일생

| 상태 | 뜻 | 어떻게 |
|---|---|---|
| 비영속(new) | 그냥 자바 객체 | `UpdateInfo.create(...)` 직후 |
| **영속(managed)** | 작업대 위 — 변경이 추적됨 | `save()` 후, 또는 `findById()` 로 조회 |
| 준영속(detached) | 트랜잭션이 끝나 작업대에서 내려옴 | 서비스 메서드가 끝난 뒤 |
| 삭제(removed) | 삭제 예약 | `delete()` |

### flush 와 saveAndFlush

**flush** = 작업대의 변경을 SQL 로 DB 에 보내는 것 (커밋 직전에 자동). 보통은 신경 쓸 일이 없지만, **그 자리에서 DB 오류를 받아야 할 때** 직접 부릅니다.

```java
info = updateRepository.saveAndFlush(...);   // INSERT 를 지금 실행 → 유니크 제약 위반을 이 try 안에서 잡음
```

우리 엔티티처럼 ID 를 DB 자동 증가(`IDENTITY`)로 만들면 `save()` 도 ID 를 얻으려고 INSERT 를 바로 실행합니다. 하지만 다른 ID 전략(시퀀스 등)에서는 INSERT 가 flush 시점(커밋 직전)까지 미뤄져, 제약 위반 예외가 `try-catch` 밖에서 터질 수 있습니다. `saveAndFlush` 는 전략과 상관없이 "지금 SQL 을 보낸다" 를 보장하므로, 제약 위반을 이 `try` 안에서 **확실히** 잡으려고 씁니다 (2부 10장).

### 정리

- JPA(Hibernate)는 객체 ↔ 테이블 변환을 해 주는 ORM.
- 트랜잭션 동안 엔티티는 영속성 컨텍스트(작업대)에 올라가 추적되고, 커밋 때 바뀐 부분이 UPDATE 된다 (더티 체킹).
- 그 자리에서 SQL 을 실행해야 하면 `saveAndFlush`.

### 확인 문제

22-1. 컨트롤러에서 `ProjectResponse` 를 받은 뒤 그 값을 바꾸면 DB 가 바뀔까요? 서비스가 돌려준 엔티티를 컨트롤러에서 바꾸면요?

---

## 23장. 연관관계, 지연 로딩, N+1

### 23.1. 연관관계 매핑 — 외래키를 객체로

DB 에서는 `update_info.developer_id = 2` 라는 **번호**지만, 자바에서는 `info.getDeveloper()` 로 **객체**를 다루고 싶습니다.

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)     // 업데이트 여러 개 → 개발자 한 명
@JoinColumn(name = "developer_id", nullable = false, updatable = false)
private AppUser developer;
```

- `@ManyToOne`: "여러 업데이트가 한 사용자를 가리킨다". DB 의 외래키와 같은 방향입니다.
- 반대 방향 `@OneToMany`(사용자 → 그가 올린 업데이트 목록)는 **일부러 만들지 않았습니다.** 필요할 때 Repository 로 조회하면 되고, 양방향은 서로 맞춰 줘야 할 것이 늘어나 실수가 잦습니다. 우리 엔티티의 연관관계는 전부 **단방향 `@ManyToOne`** 입니다.

### 23.2. 지연 로딩(LAZY)

`UpdateInfo` 를 조회할 때 개발자(`AppUser`)까지 바로 가져오지 않습니다. `info.getDeveloper().getName()` 처럼 **실제로 쓰는 순간** 쿼리를 날립니다. 쓰지 않으면 쿼리도 없습니다.
그 전까지 `developer` 자리에는 **가짜 객체(프록시)** 가 들어 있다가, 처음 쓸 때 진짜 값을 채웁니다.

`@ManyToOne` 의 기본값은 EAGER(즉시 로딩)라서, 우리는 **항상 LAZY 로 명시**합니다. EAGER 는 필요 없을 때도 조인이 붙어 쿼리가 무거워지고, 어디서 쿼리가 나가는지 예측하기 어려워집니다.

### 23.3. open-in-view = false

스프링 부트 기본값(true)은 DB 연결과 작업대를 **응답이 다 나갈 때까지** 열어 둬서, 컨트롤러에서도 지연 로딩이 됩니다. 편하지만 요청이 끝날 때까지 DB 연결을 붙잡습니다 (21장).
우리는 `false` 로 꺼 두었습니다. 그래서 **지연 로딩은 서비스의 트랜잭션 안에서 끝내야** 합니다.
→ 서비스에서 엔티티를 DTO 로 바꿔서(`UpdateResponse.from(info)`) 돌려주는 이유입니다. 트랜잭션 밖에서 `getDeveloper().getName()` 을 부르면 `LazyInitializationException` 이 납니다.

> 코드: `application.yml` 의 `spring.jpa.open-in-view: false`

### 23.4. N+1 문제와 @EntityGraph

**문제**: 업데이트 20개를 조회한 뒤(쿼리 1번), 각 업데이트의 개발자 이름을 꺼내면 지연 로딩 때문에 개발자 조회 쿼리가 **최대 20번 더** 나갑니다. 1 + N 번이라 "N+1 문제" 입니다. 목록이 길어질수록 느려지고, 테스트 데이터가 적을 때는 티가 나지 않아 운영에서 터집니다.

```sql
SELECT ... FROM update_info WHERE project_id = 3 LIMIT 20;   -- 1번
SELECT ... FROM app_user WHERE id = 2;                       -- +1
SELECT ... FROM app_user WHERE id = 5;                       -- +1
...                                                          -- N번
```

**해결: @EntityGraph** — "이 조회를 할 때는 developer 도 같이 가져와" 를 지정하면 JOIN 한 번으로 끝납니다.

```java
@EntityGraph(attributePaths = "developer")
Page<UpdateInfo> findByProjectId(Long projectId, Pageable pageable);

@EntityGraph(attributePaths = {"update", "update.project"})   // 두 단계도 가능
Page<DownloadHistory> findAll(Specification<DownloadHistory> spec, Pageable pageable);
```

**기본 전략**: 엔티티에는 LAZY 를 걸어 두고, **목록 조회처럼 함께 필요한 경우에만** 그때그때 같이 가져온다.

> 코드: `UpdateInfoRepository`, `DownloadHistoryRepository`, `UpdateHistoryRepository`, `SetupGuideRepository`, `AllowedIpRepository`

### 정리

- 연관관계는 단방향 `@ManyToOne` + LAZY 가 기본.
- open-in-view 를 끄면 지연 로딩은 트랜잭션 안에서 끝내고 DTO 로 바꿔 내보낸다.
- 목록에서 연관 엔티티를 쓰면 N+1 → `@EntityGraph` 로 한 번에.

### 확인 문제

23-1. 다운로드 이력 목록은 "업데이트 버전" 과 "프로젝트 이름" 을 함께 보여 줍니다. `@EntityGraph` 를 빼면 20건 목록에서 SQL 이 최대 몇 번 나갈까요? (힌트: 다운로드 이력 → 업데이트 → 프로젝트)

---

## 24장. 엔티티 설계 습관

| 습관 | 이유 | 예 |
|---|---|---|
| 기본 생성자는 `protected` | JPA 는 기본 생성자가 필요하지만, 외부에서 빈 객체를 만들지 못하게 | `@NoArgsConstructor(access = AccessLevel.PROTECTED)` |
| setter 대신 의미 있는 메서드 | "무엇을 하는지" 가 이름에 드러나고, 규칙을 한곳에 모음 | `user.recordLoginFailure(...)`, `info.changeStatus(...)` |
| 생성은 정적 팩토리 메서드 | 필수 값과 초기 상태(ACTIVE 등)를 한곳에서 보장 | `UpdateInfo.create(...)` 는 항상 `status = ACTIVE` 로 시작 |
| `@Enumerated(EnumType.STRING)` | 기본값(ORDINAL)은 숫자로 저장돼서, enum 순서를 바꾸면 기존 데이터 의미가 바뀜 | `Role`, `UpdateStatus` |
| `updatable = false` | 등록 후 바꾸면 안 되는 값을 JPA 수준에서 막음 | 업데이트의 파일 키·체크섬·버전·프로젝트 |
| 컬럼 길이 = DTO 검증 길이 | DB 에서 잘리는 오류 대신 친절한 400 | `title` 200자 ↔ `@Size(max = 200)` |
| 공통 컬럼은 상속 | `created_at`, `updated_at` 을 모든 엔티티에 반복하지 않음 | `BaseTimeEntity` + JPA Auditing |
| 업무 규칙을 엔티티 안에 | 규칙이 데이터 옆에 있어 흩어지지 않음 | `AppUser.isLocked(now)`, `UpdateInfo.isDownloadable()` |

**좋은 예: 로그인 잠금 규칙이 엔티티 안에 있다**

```java
public void recordLoginFailure(int maxFailures, Duration lockDuration, LocalDateTime now) {
    if (lockedUntil != null && !lockedUntil.isAfter(now)) {   // 이전 잠금이 풀린 뒤라면
        lockedUntil = null;                                     // 처음부터 다시 셈
        failedLoginCount = 0;
    }
    failedLoginCount++;
    if (failedLoginCount >= maxFailures) {
        lockedUntil = now.plus(lockDuration);
    }
}
```

서비스는 `user.recordLoginFailure(...)` 한 줄만 부르고, "몇 번이면 잠그는지, 언제 다시 세는지" 는 엔티티가 압니다. `now` 를 파라미터로 받는 것도 습관입니다 — 테스트에서 시간을 마음대로 넣을 수 있습니다.

**JPA Auditing**: `@CreatedDate`, `@LastModifiedDate` 를 붙이면 저장·수정 시각을 자동으로 채웁니다. `@EnableJpaAuditing`(우리는 `JpaConfig`)이 켜져 있어야 동작합니다 (2부 5장의 "읽는 쪽이 꺼져 있으면").

### 확인 문제

24-1. `AppUser` 에 `@Setter` 를 달면 어떤 곳에서 어떤 실수가 가능해질까요?

---

## 25장. Spring Data JPA 로 조회하기

### 25.1. Repository 인터페이스 — 구현은 스프링이 만든다

```java
public interface UpdateInfoRepository extends JpaRepository<UpdateInfo, Long> {
    boolean existsByProjectIdAndVersion(Long projectId, String version);
}
```

**구현 클래스가 없습니다.** 스프링 데이터가 앱 시작 때 이 인터페이스의 구현 객체를 만들어 빈으로 등록합니다 (2부 6장의 다형성). `JpaRepository` 를 상속하면 `save`, `findById`, `findAll`, `delete` 등이 기본으로 생깁니다.

### 25.2. 메서드 이름 쿼리 — 이름이 곧 쿼리

스프링 데이터는 메서드 이름을 **키워드로 쪼개서** 쿼리를 만듭니다.

| 메서드 | 만들어지는 조건 |
|---|---|
| `findByEmail(email)` | `WHERE email = ?` |
| `existsByProjectIdAndVersion(p, v)` | `WHERE project_id = ? AND version = ?` 이 있는지 |
| `findByEnabledTrueOrderByNameAsc()` | `WHERE enabled = true ORDER BY name` |
| `findByProjectIdOrderByIdAsc(id)` | `WHERE project_id = ? ORDER BY id` |

**함정: 필드 이름에 키워드가 들어 있으면**
허용 IP 의 필드 이름이 `ipOrCidr` 였는데, `existsByIpOrCidr(...)` 라고 짓자 앱이 시작조차 하지 못했습니다.

```
No property 'ip' found for type 'AllowedIp'; Did you mean 'id'
```

스프링 데이터가 `IpOrCidr` 를 "`ip` **Or** `cidr`" 로 읽었기 때문입니다. 이럴 때는 `@Query` 로 직접 씁니다.

### 25.3. @Query — 직접 쓰는 쿼리 (JPQL)

```java
@Query("select count(a) > 0 from AllowedIp a where a.ipOrCidr = :value")
boolean existsByValue(@Param("value") String value);
```

- **JPQL**: SQL 과 비슷하지만 테이블·컬럼 대신 **엔티티·필드 이름**으로 씁니다 (`AllowedIp`, `ipOrCidr` — `allowed_ip`, `ip_or_cidr` 아님). Hibernate 가 SQL 로 바꿉니다.
- `:value` 는 이름 붙은 파라미터. 값은 항상 따로 전달(바인딩)되므로 SQL 인젝션 걱정이 없습니다 (5부 29장).
- 이런 실수는 **앱 시작 시점**에 바로 드러납니다(Repository 를 만들 때 메서드 이름을 해석하므로). 테스트가 즉시 실패해서 알 수 있었습니다.

### 25.4. 페이징 (Pageable)

```java
Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
Page<UpdateInfo> result = updateRepository.findByProjectId(projectId, pageable);
```

- Repository 메서드에 `Pageable` 을 넘기면 `LIMIT/OFFSET` 쿼리와 **전체 개수 쿼리(COUNT)** 를 함께 실행합니다.
- 정렬에 `id` 를 두 번째 기준으로 넣은 이유: `createdAt` 이 같은 행이 있으면 DB 가 순서를 마음대로 정해서, 페이지를 넘길 때 **같은 행이 두 번 나오거나 빠질 수** 있습니다 (19장 확인 문제의 답).
- `size` 는 서버에서 최대 100 으로 제한 → `size=1000000` 같은 요청으로 서버를 괴롭히지 못하게. 음수 page 도 0 으로.
- `Page` 를 그대로 JSON 으로 내보내지 않고 `PageResponse` record 로 바꿉니다 (스프링 버전마다 `Page` 의 JSON 구조가 달라질 수 있음).

### 25.5. 동적 검색 조건 (Specification)

다운로드 이력은 프로젝트·업데이트·사용자·기간 중 **아무 조합**으로나 검색할 수 있어야 합니다. 메서드 이름 방식으로는 조합마다 메서드가 필요해(2⁴ = 16개) 감당이 안 됩니다.

**Specification**: 조건 하나를 "조각" 으로 만들고, 값이 없으면 `null` 을 돌려 **그 조건을 빼 버립니다** (람다는 2부 7장).

```java
static Specification<DownloadHistory> userId(Long userId) {
    return (root, query, cb) -> userId == null ? null : cb.equal(root.get("user").get("id"), userId);
}

Specification<DownloadHistory> spec = Specification.allOf(
        projectId(projectId), updateId(updateId), userId(userId), downloadedBetween(from, toExclusive));
downloadHistoryRepository.findAll(spec, pageable);   // JpaSpecificationExecutor 를 상속하면 사용 가능
```

**날짜 조건 팁**: "10/3 ~ 10/3" 검색은 `>= 10/3 00:00` 그리고 `< 10/4 00:00` 으로 바꿉니다. `<= 10/3 23:59:59` 로 하면 밀리초 단위 데이터(23:59:59.5)가 빠질 수 있습니다.

**LIKE 이스케이프**: 사용자 검색(`UserSpecs.keyword()`)은 `LIKE '%검색어%'` 를 씁니다. 사용자가 `%` 나 `_` 를 입력하면 "아무 글자" 로 해석되어 전부 검색되므로, 특수문자 앞에 `\` 를 붙이고 `cb.like(..., pattern, '\\')` 로 이스케이프 문자를 지정해 글자 그대로 찾습니다. (SQL 인젝션은 아닙니다 — 값은 바인딩되므로 안전. 검색 결과가 틀려지는 문제)

### 무엇을 언제 쓰나

| 상황 | 도구 |
|---|---|
| 조건 1~2개, 고정 | 메서드 이름 쿼리 |
| 이름이 길어지거나 애매함, 집계 | `@Query` |
| 조건이 있을 수도 없을 수도 (검색 화면) | Specification |
| 아주 복잡한 동적 쿼리 | QueryDSL 같은 라이브러리 (부록 "다음에 공부할 것") |

> 코드: `update/DownloadHistorySpecs.java`, `DownloadService.search()`, `user/UserSpecs.java`, `ipfilter/AllowedIpRepository.java`

### 확인 문제

25-1. 다운로드 이력 검색에서 `size` 를 제한하지 않으면 어떤 요청으로 서버를 힘들게 할 수 있을까요?

---

## 26장. 스키마 관리 — 테이블은 누가 만드나

엔티티를 고쳤는데 DB 테이블은 그대로라면? 둘을 맞추는 방법은 세 가지입니다.

| 방법 | 동작 | 장점 | 단점 |
|---|---|---|---|
| `ddl-auto: update` (지금) | 시작할 때 엔티티를 보고 없는 테이블·컬럼을 **추가** | 편함 | 이름 변경·삭제는 안 함. 의도치 않은 변경이 운영 DB 에 바로 들어갈 수 있음 |
| `ddl-auto: validate` | 엔티티와 테이블이 다르면 **시작 실패** | 안전 | 테이블은 다른 방법으로 만들어야 함 |
| **마이그레이션 도구 (Flyway)** | `V1__init.sql`, `V2__add_column.sql` 처럼 **변경 SQL 을 버전별 파일로** 관리, 아직 적용 안 된 것만 순서대로 실행 | 변경 이력이 Git 에 남고, 모든 환경이 같은 순서로 바뀜 | 파일을 직접 써야 함 |

- `create`/`create-drop` 은 테이블을 **지우고** 다시 만들므로 테스트(H2) 외에는 쓰면 안 됩니다.
- 운영이 안정되면 `JPA_DDL_AUTO=validate` + 마이그레이션으로 가는 것이 정석입니다 (`docs/operations.md` 6장).
- 2차 개발(설치 마법사)에서 현장 DB 를 업데이트할 때 바로 이 **Flyway 방식**을 씁니다 (`Phase2-Spec/db/README.md`). MariaDB 는 테이블 구조 변경(DDL)이 트랜잭션으로 롤백되지 않기 때문에, "한 파일에 한 변경 + 미리 백업" 규칙을 둡니다. 20장의 트랜잭션이 **모든 것을 되돌려 주지는 않는다**는 좋은 예입니다.

### 정리

- 개발 초기엔 `update` 가 편하지만, 운영은 `validate` + 마이그레이션 파일.
- DDL 은 롤백되지 않는 DB 가 많다 → 백업과 작은 단위 변경.

---

[← 3부](03-spring-core.md) · [목차](../backend-concepts.md) · [5부. 보안 입문 →](05-security.md)
