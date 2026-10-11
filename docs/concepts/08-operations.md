# 8부. 운영

[← 7부](07-testing.md) · [목차](../backend-concepts.md) · [부록 →](09-appendix.md)

코드가 완성돼도 일은 끝나지 않습니다. 실제 서버에서 **빌드하고, 띄우고, 문제가 생기면 원인을 찾고, 느리지 않은지 확인**해야 합니다. 이 부는 "내 PC 에서 되는 코드" 를 "운영에서 믿을 수 있는 서비스" 로 만드는 이야기입니다.

---

## 38장. 빌드 — Gradle 과 실행 가능한 jar

### 이야기

우리 코드는 스프링, Hibernate, Jackson, MariaDB 드라이버 ... 수십 개 라이브러리에 기대고 있습니다. 이걸 직접 내려받아 버전을 맞추고 컴파일 경로에 넣는 일을 대신 해 주는 것이 **빌드 도구 Gradle** 입니다.

### build.gradle 읽기

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.1'                // 실행 가능한 jar 만들기 등
    id 'io.spring.dependency-management' version '1.1.7'       // 라이브러리 버전을 스프링 부트에 맞춰 자동 선택
}
java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }   // Java 17 로 컴파일

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'   // 컴파일 + 실행에 필요
    compileOnly 'org.projectlombok:lombok'                                 // 컴파일 때만 (2부 9장)
    annotationProcessor 'org.projectlombok:lombok'                         // 컴파일 때 코드를 만들어 주는 도구
    runtimeOnly 'org.mariadb.jdbc:mariadb-java-client'                     // 실행 때만 (코드에서 직접 안 씀)
    testImplementation '...-webmvc-test'                                   // 테스트 때만
    testRuntimeOnly 'com.h2database:h2'
}
```

| 범위 | 언제 필요 | 예 |
|---|---|---|
| `implementation` | 컴파일 + 실행 | 스프링 스타터들 |
| `compileOnly` + `annotationProcessor` | 컴파일 때만 | Lombok (실행 jar 에 안 들어감) |
| `runtimeOnly` | 실행 때만 | DB 드라이버 (JDBC 표준 인터페이스로만 쓰니까, 2부 6장) |
| `test...` | 테스트 때만 | H2, MockMvc |

라이브러리 버전을 적지 않아도 되는 이유: `dependency-management` 플러그인이 **스프링 부트 4.1.1 과 함께 검증된 버전**을 골라 줍니다. 버전 충돌을 피하는 큰 장점입니다.

### 실행 가능한 jar (fat jar)

```
.\gradlew.bat bootJar   →  build\libs\RemoteUpdateBackend-0.0.1-SNAPSHOT.jar
```

이 jar 하나에 **우리 코드 + 모든 라이브러리 + 내장 톰캣**이 들어 있습니다(그래서 fat jar). 서버에는 Java 만 있으면 `java -jar ...jar` 로 실행됩니다 (3부 11장).
- `gradlew`(Gradle Wrapper)는 프로젝트가 정한 Gradle 버전을 자동으로 내려받아 씁니다. 그래서 PC 마다 Gradle 을 따로 설치하지 않아도 같은 결과가 나옵니다.
- 2차 개발에서는 jar 에 **빌드 정보(버전·시각)** 를 넣어(`springBoot { buildInfo() }`), 업로드 서버가 jar 의 실제 버전을 확인합니다 (`Phase2-Spec/build-info.md`).

---

## 39장. 로그 — 나중의 나를 위한 기록

### 이야기

운영 서버에서 "어제 오후에 업로드가 실패했대요" 라는 연락이 옵니다. 그때 무슨 일이 있었는지 알 수 있는 유일한 단서가 **로그**입니다. 디버거를 붙일 수도, 그 순간으로 돌아갈 수도 없습니다.

### 개념: 로그 레벨

```java
@Slf4j                    // Lombok 이 log 변수를 만들어 줌 (SLF4J + Logback)
public class UpdateService {
    log.info("업데이트 등록: projectId={}, version={}, size={}, by userId={}", projectId, version, stored.size(), author.id());
    log.warn("업데이트 등록 롤백으로 저장 파일 삭제: key={}", key);
    log.error("파일 저장 실패: key={}", key, e);      // 마지막에 예외 객체 → 스택까지 기록
}
```

| 레벨 | 언제 | 우리 예 |
|---|---|---|
| `ERROR` | 사람이 바로 봐야 하는 실패 | 파일 저장 실패, 처리되지 않은 예외 |
| `WARN` | 이상하지만 처리는 됨 | 허용되지 않은 IP 의 관리 요청 차단, 롤백으로 파일 정리 |
| `INFO` | 중요한 업무 사건 | 업데이트 등록·상태 변경, 로그인 실패 |
| `DEBUG` | 개발 중 자세히 | SQL (`org.hibernate.SQL`) |

설정한 레벨 **이상만** 기록됩니다. `application.yml` 의 `logging.level.com.onpoom.remoteupdate: DEBUG` 처럼 패키지별로 정합니다.

### 로그를 쓰는 습관

- **`{}` 자리표시자**를 쓴다. `"... " + version` 처럼 이어 붙이면 레벨이 꺼져 있어도 문자열을 만드는 비용이 듭니다.
- **누가, 무엇을, 어떤 대상에** 를 남긴다 (`by userId=`, `projectId=`). "실패했음" 만으로는 아무것도 알 수 없습니다.
- **비밀번호·토큰·세션 ID 는 절대 남기지 않는다.** 로그 파일은 DB 보다 허술하게 관리되기 쉽습니다. 로그인 실패 로그에도 이메일 대신 `userId` 만 남겼습니다.
- **예외는 한 번만** 기록한다. 잡을 때마다 기록하면 같은 오류가 여러 번 찍혀 헷갈립니다. 우리는 `GlobalExceptionHandler` 에서 예상 못 한 예외만 ERROR 로 남깁니다.

> 운영 로그 위치·보관: `docs/operations.md` 8장

---

## 40장. 개발 환경과 운영 환경은 다르다

### 실제로 만난 버그

Phase 6 에서 운영 방식(Spring 하나가 화면까지 제공)으로 띄우자마자, 개발 중에는 한 번도 안 보이던 버그가 나왔습니다.

| | 개발 | 운영 |
|---|---|---|
| 화면 파일(js, css) | Vite 개발 서버(5173)가 제공 | **Spring(8080)이 제공 → 보안 필터를 통과** |
| DB | 테스트는 H2 | MariaDB |
| 쿠키 | http | https 라면 `Secure` 필요 |

"비밀번호 변경 필요" 필터가 모든 요청을 검사했기 때문에, 운영에서는 **화면 파일까지 막혀** 비밀번호 변경 화면을 띄울 수 없었습니다.
해결: `shouldNotFilter()` 로 `/api/` 가 아닌 요청은 건너뜀 (3부 15장). 덕분에 세션 동기화 필터가 화면 파일 요청마다 DB 를 조회하던 낭비도 함께 없어졌습니다.

**교훈: 운영과 같은 구성으로 한 번은 끝까지 띄워 봐야 한다.** 테스트가 아무리 많아도 구성이 다르면 놓치는 것이 있습니다. 그 뒤로 이 상황을 재현하는 테스트(`FrontendServingTest`)를 남겼습니다 (7부 37장).

### SPA fallback

Vue 라우터 주소(`/projects/3`)는 실제 파일이 아니라서, 그 주소에서 새로고침하면 서버가 404 를 줍니다. 서버가 "없는 파일이면 `index.html`" 을 주도록 해야 화면이 뜹니다.
단, `/api` 주소까지 `index.html` 로 바꾸면 없는 API 가 200 HTML 로 응답되는 이상한 일이 생기므로 제외합니다. 또 파일 이름에 해시가 붙은 js/css 는 1년 캐시, `index.html` 은 캐시하지 않게 해서 새 버전을 배포하면 바로 반영되게 했습니다.
> 코드: `config/web/FrontendConfig.java`

### 같은 jar, 다른 설정

같은 jar 를 개발·운영에서 쓰고, 차이(DB 주소, 저장 위치, HTTPS 쿠키, 관리자 비밀번호)는 **환경 변수로만** 바꿉니다 (3부 14장). 코드나 설정 파일을 고쳐서 배포하지 않습니다. "운영용으로 따로 빌드한 jar" 는 테스트한 jar 와 다른 물건이 되기 때문입니다.
> 문서: `docs/operations.md`

---

## 41장. 성능을 숫자로 확인하기

"느리지 않을 것 같다" 가 아니라 **측정해서** 확인했습니다.

### 메모리: 힙을 일부러 작게 잡고 큰 파일 보내기

`java -Xmx256m`(힙 최대 256MB)으로 띄우고 490MB 파일을 올리고 받았습니다.
파일을 `byte[]` 로 통째로 읽는 코드였다면 `OutOfMemoryError` 로 죽었을 것입니다. 스트리밍(6부 31~32장) 덕분에 프로세스 메모리는 약 300MB 에서 멈췄습니다.

### SQL 수: 요청 하나에 쿼리가 몇 번 나가나

`logging.level.org.hibernate.SQL=DEBUG` 로 실행하면 Hibernate 가 실행하는 SQL 이 로그에 찍힙니다 (4부 19장).
요청 전후 로그 줄 수를 세어 보니 조회 API 는 3~4회, 등록·다운로드처럼 쓰는 API 는 확인·저장을 합쳐 4~6회였고, 무엇보다 **목록이 길어져도 늘지 않았습니다** (N+1 없음, 4부 23장).
그중 1회는 세션 동기화 필터(5부 30장)의 사용자 조회입니다 — "기능을 위해 감수한 비용" 이 실제로 얼마인지도 숫자로 확인한 셈입니다.

| 확인할 것 | 방법 |
|---|---|
| 메모리 | `-Xmx` 로 힙 제한, `ps` / 작업 관리자로 프로세스 메모리 |
| SQL 수 | `org.hibernate.SQL=DEBUG` 로그 |
| 응답 시간 | `curl -w '%{time_total}'`, 브라우저 개발자 도구 Network 탭 (1부 1장) |

**추측보다 측정.** 성능 문제는 대부분 예상하지 못한 곳에서 나옵니다. 먼저 재고, 느린 곳을 찾고, 고친 뒤 다시 잽니다.

---

## 42장. 서버 앞에 서는 것들 — 포트, 리버스 프록시, HTTPS

### 포트

한 컴퓨터에서 여러 프로그램이 네트워크를 쓰므로 **포트 번호**로 구분합니다. RemoteUpdate 는 8080, MariaDB 는 3306, 웹 기본은 80(http)/443(https).

### 리버스 프록시

서버 앞에 **nginx** 같은 프로그램을 두고, 바깥 요청을 먼저 받아 안쪽 서버로 넘겨주게 할 수 있습니다.

```
브라우저 ──▶ nginx :80/443 ──▶ 127.0.0.1:8080 (스프링)
```

| 왜 | 설명 |
|---|---|
| 포트 숨기기 | 바깥에는 80/443 하나만 열고 실제 서비스 포트는 안쪽에만 |
| 한 주소로 여러 서비스 | `/orderApi` → 36100, `/termApi` → 36101 ... (2차 개발의 현장 구성이 바로 이것, `Phase2-Spec/templates/nginx.conf.tpl`) |
| HTTPS 처리 | 인증서를 nginx 한곳에서 관리 |
| 정적 파일 | 화면 파일은 nginx 가 직접 빠르게 제공 |

이때 스프링이 보는 접속 IP 는 **nginx 의 IP(127.0.0.1)** 가 됩니다. 진짜 IP 는 nginx 가 `X-Forwarded-For` 헤더에 담아 주는데, 5부 30장처럼 **신뢰하는 프록시에서 온 요청일 때만** 읽어야 합니다 (`app.security.ip-filter.trusted-proxies`).
또 서비스가 `server.address: 127.0.0.1` 로 안쪽 주소에만 귀를 열면, 바깥에서 36100 으로 직접 들어오는 길이 아예 막힙니다.

### HTTPS

http 는 네트워크 중간에서 내용(비밀번호, 세션 쿠키)이 그대로 보입니다. HTTPS 는 **암호화**해서 이를 막고, 인증서로 **진짜 그 서버인지** 확인합니다.
HTTPS 로 운영할 때는 세션 쿠키에 `Secure` 를 켜서(`COOKIE_SECURE=true`) 암호화된 연결에서만 쿠키가 오가게 합니다 (1부 4장).
> 문서: `docs/operations.md` 4장

---

## 43장. 프런트와 백엔드의 역할 분담

Phase 4 에서 Vue 화면을 붙이면서 "같은 검사를 왜 양쪽에서 하나?" 가 자주 나왔습니다. 마지막 장에서 전체를 정리합니다.

| 검사 | 프런트(Vue) | 백엔드(Spring) |
|---|---|---|
| 로그인 여부 | 라우터 가드가 로그인 화면으로 보냄 | 필터가 401 |
| 역할 | 개발자 메뉴·상태 필터를 숨김 | URL 규칙 + `@PreAuthorize` 가 403 |
| 허용 IP | 노란 안내줄을 미리 보여 줌 | `IpRestrictionFilter` 가 403 |
| 비밀번호 규칙 | 입력 중에 바로 안내 | `@Pattern` 이 400 |
| 파일 형식·크기 | 고르는 즉시 안내 | `UploadValidator`, multipart 제한 |
| 배포 중단된 업데이트 | 다운로드 버튼을 안 보여 줌 | 다운로드 요청 자체를 거부 |

**원칙: 프런트 검사는 "편의", 백엔드 검사는 "보안".**
브라우저 코드는 사용자가 개발자 도구로 얼마든지 바꾸거나 건너뛸 수 있습니다(5부 원칙 1). 프런트에서 버튼을 숨겨도 주소를 직접 입력하면 요청은 갑니다. 그래서 **보안 판단은 반드시 서버가** 하고, 프런트는 사용자가 헛걸음하지 않게 미리 안내하는 역할입니다.

**서버 응답이 진실**: 프런트의 로그인 상태(Pinia `auth` 스토어)는 서버 세션의 "사본" 입니다. 세션이 만료되면 서버는 401 을 주고, 프런트는 그걸 받아(axios 응답 인터셉터) 사본을 지우고 로그인 화면으로 보냅니다.

**응답 형식을 맞춰 두면 편한 점**: 모든 오류가 `{ code, message }` 라서(1부 2장, 3부 17장) 프런트는 `errorMessage(e)` 하나로 서버 메시지를 그대로 보여 줄 수 있고, 특정 상황은 `code` 로 분기합니다 (예: `PASSWORD_CHANGE_REQUIRED` → 비밀번호 화면).

> 코드: Frontend `src/router/index.ts`(가드), `src/api/http.ts`(인터셉터), `src/stores/auth.ts`

### 확인 문제

43-1. 프런트에서 "관리자만 사용자 관리 메뉴를 보이게" 처리했으니, 백엔드의 `hasRole('ADMIN')` 은 빼도 될까요?

---

[← 7부](07-testing.md) · [목차](../backend-concepts.md) · [부록 →](09-appendix.md)
