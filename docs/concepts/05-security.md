# 5부. 보안 입문

[← 4부](04-database-jpa.md) · [목차](../backend-concepts.md) · [6부 →](06-files-io.md)

지금까지는 "올바르게 동작하는 서버" 를 만들었습니다. 이 부에서는 **"나쁜 의도를 가진 요청에도 올바르게 동작하는 서버"** 를 다룹니다.
보안은 범위가 넓어서, 여기서는 **흐름과 원리**를 잡고 세부 내용은 [security-guide.md](../security-guide.md) 로 연결합니다. 이 부를 먼저 읽고 security-guide 를 읽으면 훨씬 쉽게 읽힙니다.

**보안의 기본 마음가짐 세 가지**

1. **클라이언트를 믿지 않는다.** 브라우저에서 오는 모든 것(주소, 본문, 헤더, 쿠키)은 사용자가 바꿀 수 있다.
2. **한 겹만 믿지 않는다.** 한 곳이 뚫려도 다음 겹이 막도록 (다층 방어).
3. **실패해도 안전한 쪽으로.** 판단이 애매하면 막는다 (예: 규칙에 없는 요청은 `denyAll()`).

---

## 27장. 인증과 인가, 그리고 필터 체인

### 이야기

회사 건물을 생각해 보세요. 입구에서 **사원증으로 누구인지 확인**하고(인증), 층마다 **들어갈 자격이 있는지 확인**합니다(인가).

### 개념

| | 인증 (Authentication) | 인가 (Authorization) |
|---|---|---|
| 질문 | **너 누구야?** | **너 이거 해도 돼?** |
| 우리 방법 | 이메일 + 비밀번호 → 세션 (1부 4장) | 역할(직원/개발자/관리자) + 허용 IP |
| 실패 시 | **401** Unauthorized | **403** Forbidden |
| 코드 | `AuthService.authenticate()`, `SessionAuthenticator` | URL 규칙, `@PreAuthorize`, `IpRestrictionFilter` |

### 스프링 시큐리티 = 필터들의 줄

3부 15장에서 본 필터 체인이 바로 스프링 시큐리티입니다. 요청은 컨트롤러에 닿기 전에 아래 줄을 차례로 지납니다 (`SecurityConfig` 에서 구성).

```
요청 ─▶ 세션에서 로그인 정보 복원 ─▶ CSRF 검사 ─▶ 로그아웃 처리
     ─▶ [우리] SessionUserRefreshFilter : 세션 정보를 DB 와 맞춤 (30장)
     ─▶ AuthorizationFilter             : URL 규칙으로 권한 검사 → 401/403
     ─▶ [우리] IpRestrictionFilter      : 관리 기능이면 허용 IP 인지 (30장)
     ─▶ [우리] PasswordChangeRequiredFilter : 임시 비밀번호면 인증 API 외 차단
     ─▶ DispatcherServlet ─▶ @PreAuthorize (메서드 단위 권한) ─▶ 컨트롤러
```

**URL 규칙** — 위에서부터 처음 맞는 규칙이 적용됩니다.

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()     // CSRF 쿠키 발급은 누구나
        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()   // 로그인은 누구나
        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")                 // 관리자 API
        .requestMatchers("/api/**").authenticated()                           // 나머지 API 는 로그인 필요
        .requestMatchers(HttpMethod.GET, "/**").permitAll()                   // 화면 파일은 누구나
        .anyRequest().denyAll())                                              // 그 외는 거부 (실패해도 안전하게)
```

**역할 계층**: 관리자는 개발자 기능을, 개발자는 직원 기능을 모두 쓸 수 있어야 합니다. `RoleHierarchy` 로 `ADMIN > DEVELOPER > STAFF` 를 선언해 두면 `hasRole('DEVELOPER')` 에 관리자도 통과합니다.

**순서가 중요하다**: 세션 갱신 필터는 권한 검사 **앞**에 있어야 합니다. 뒤에 있으면 옛 역할로 이미 통과한 뒤에 갱신하게 됩니다. IP 필터는 권한 검사 **뒤**에 둬서, 로그인 안 한 요청은 먼저 401 을 받게 했습니다.

> 자세히: [security-guide.md 1~2장, 8장](../security-guide.md)

### 확인 문제

27-1. 직원이 `GET /api/v1/admin/users` 를 요청하면 어느 필터에서, 몇 번 상태 코드로 거부될까요?

---

## 28장. 비밀번호는 저장하지 않는다 — 해시

### 이야기

DB 가 통째로 유출됐다고 가정합시다. 비밀번호가 그대로 저장돼 있다면, 공격자는 모든 계정으로 로그인할 수 있고, 같은 비밀번호를 쓰는 **다른 사이트**까지 뚫립니다.
그래서 비밀번호는 **원래 값을 알 수 없는 형태**로만 저장합니다.

### 개념: 해시

**해시 함수**는 입력을 고정 길이의 값으로 바꾸는 **일방향** 함수입니다. 같은 입력은 항상 같은 결과, 결과에서 입력을 되돌릴 수 없습니다.
로그인할 때는 입력한 비밀번호를 같은 방식으로 해시해 **저장된 해시와 비교**합니다. 서버도 원래 비밀번호를 모릅니다.

그런데 SHA-256 같은 일반 해시를 그냥 쓰면 안 됩니다.

| 문제 | 해결 | BCrypt 가 해 주는 것 |
|---|---|---|
| 같은 비밀번호는 같은 해시 → "자주 쓰는 비밀번호 해시표" 로 한 번에 찾음 | **솔트(salt)**: 사용자마다 다른 랜덤 값을 섞음 | 해시 안에 솔트가 함께 들어 있음 |
| 일반 해시는 너무 빨라서 초당 수십억 번 대입 가능 | **일부러 느리게** | 비용(cost) 값으로 계산량 조절 (기본 10 = 2¹⁰ 번 반복) |

```
$2a$10$N9qo8uLOickgx2ZMRZoMye.IjZAgcfl7p92ldGxad68LJZdL17lhW
 │   │  └ 솔트(22자) + 해시
 │   └ 비용
 └ 알고리즘 버전
```

```java
passwordEncoder.encode("AdminPass1")                  // 저장할 때 → 매번 다른 해시 (솔트가 달라서)
passwordEncoder.matches("AdminPass1", savedHash)      // 로그인할 때 → true/false
```

> SHA-256 은 **파일 손상 확인**(6부)처럼 "빠르고 같은 값" 이 필요한 곳에 쓰고, 비밀번호에는 BCrypt 처럼 느린 전용 해시를 씁니다. 목적이 다릅니다.

### 응답 시간으로 정보가 새는 것 막기 (타이밍 공격)

없는 이메일로 로그인하면 DB 조회 후 바로 실패, 있는 이메일이면 BCrypt 비교(약 0.1초)까지 하고 실패 — **응답 시간 차이**로 "이 이메일은 가입돼 있다" 를 알아낼 수 있습니다.
그래서 `AuthService` 는 사용자가 없을 때도 **미리 만들어 둔 가짜 해시와 비교**를 한 번 해서 시간을 맞춥니다. 메시지도 "이메일 또는 비밀번호가 올바르지 않습니다" 로 어느 쪽이 틀렸는지 알려 주지 않습니다.

```java
if (user == null) {
    passwordEncoder.matches(request.password(), dummyHash);   // 시간 맞추기용
    throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
}
```

### 대입 공격 막기: 잠금

5번 연속 틀리면 15분 잠급니다 (`AppUser.recordLoginFailure`, 4부 24장). 초당 수천 번 시도하던 공격이 15분에 5번으로 줄어듭니다.

### 추측할 수 없는 임시 비밀번호: SecureRandom

`java.util.Random` 은 내부 상태를 알면 다음 값을 예측할 수 있습니다. 비밀번호·토큰처럼 **추측되면 안 되는 값**은 반드시 `SecureRandom` 을 씁니다 (`TemporaryPasswordGenerator`).

> 자세히: [security-guide.md 6~7장](../security-guide.md)

### 확인 문제

28-1. 관리자가 "사용자 비밀번호를 잊었다고 하니 원래 비밀번호를 알려 주자" 고 할 때, 왜 불가능하고 대신 무엇을 하나요?

---

## 29장. 웹 공격 세 가지 — CSRF, XSS, SQL 인젝션

### 29.1. CSRF — 사용자 몰래 요청 보내기

**공격**: 1부 4장에서 "브라우저는 쿠키를 **자동으로** 붙인다" 고 했습니다. 개발자가 RemoteUpdate 에 로그인한 채로 악성 사이트를 열면, 그 사이트가 몰래 `POST http://우리서버/api/v1/updates/12/status` 를 보내게 할 수 있습니다. 브라우저는 우리 서버의 세션 쿠키를 자동으로 붙이므로, 서버는 개발자가 보낸 요청으로 믿습니다.

**방어: "쿠키 값을 헤더에 다시 써서 보내라"**
1. 서버가 `XSRF-TOKEN` 쿠키에 랜덤 토큰을 줍니다.
2. 우리 화면(같은 사이트)의 자바스크립트는 이 쿠키를 **읽어서** `X-XSRF-TOKEN` 헤더에 넣어 보냅니다.
3. 악성 사이트는 쿠키를 자동으로 붙일 수는 있어도 **읽을 수는 없어서** 헤더를 만들 수 없습니다 → 403 `CSRF_INVALID`.

`SameSite=Lax` 쿠키 옵션(1부 4장)이 한 겹 더 막아 줍니다. 상태를 바꾸는 요청(POST/PUT/PATCH/DELETE)만 검사하므로, **GET 으로 데이터를 바꾸는 API 를 만들면 안 됩니다.**

> 자세히: [security-guide.md 4장](../security-guide.md#4-csrf-cross-site-request-forgery)

### 29.2. XSS — 화면에 스크립트 심기

**공격**: 가이드 본문에 `<script>세션을 훔치는 코드</script>` 를 넣으면, 그 가이드를 여는 모든 사람의 브라우저에서 실행됩니다.

**방어**
- 마크다운을 HTML 로 바꿀 때 **HTML 태그를 글자로 취급** (Frontend `markdown-it` 의 `html: false`) → `<script>` 가 화면에 글자로 보일 뿐 실행되지 않음
- 세션 쿠키는 `HttpOnly` → 스크립트가 실행돼도 쿠키를 읽을 수 없음 (다층 방어)

E2E 테스트에서 실제로 `<script>` 를 넣어 실행되지 않는 것을 확인했습니다 (7부 36장).

### 29.3. SQL 인젝션 — 입력으로 SQL 바꾸기

**공격**: 문자열을 이어 붙여 SQL 을 만들면,

```java
"SELECT * FROM app_user WHERE email = '" + email + "'"     // ❌
// email 에  ' OR '1'='1  을 넣으면 → WHERE email = '' OR '1'='1'  → 전체 조회
```

**방어: 값은 항상 파라미터로 따로 보낸다(바인딩).** JPA 의 메서드 이름 쿼리, `@Query` 의 `:value`, Specification 은 모두 값을 바인딩하므로 입력이 SQL 문법으로 해석되지 않습니다. 우리 코드에는 문자열로 SQL 을 조립하는 곳이 없습니다.

### 정리

| 공격 | 이용하는 것 | 우리 방어 |
|---|---|---|
| CSRF | 쿠키 자동 전송 | XSRF 토큰 헤더 + SameSite |
| XSS | 사용자 입력을 HTML 로 출력 | HTML 태그 무시 + HttpOnly |
| SQL 인젝션 | 입력을 SQL 에 이어 붙임 | 항상 바인딩 (JPA) |

### 확인 문제

29-1. "다운로드 이력 삭제" 기능을 `GET /api/v1/downloads/5/delete` 로 만들면 CSRF 방어가 왜 소용없어질까요?

---

## 30장. 우리 코드의 보안 장치들

여기까지의 원리가 RemoteUpdate 에서 어떻게 쓰였는지 모아 봅니다.

### 30.1. 세션 사본 문제 — 권한 변경을 즉시 반영하기

**문제**: 로그인할 때 세션에 사용자 정보(역할 등)를 **복사해** 넣습니다. 이후 요청은 DB 가 아니라 이 사본으로 권한을 판단합니다. 그래서 관리자가 계정을 중지하거나 역할을 낮춰도, 이미 로그인한 사람은 세션이 끝날 때까지(최대 30분) 옛 권한으로 계속 쓸 수 있습니다.

| 해결 방법 | 장점 | 단점 |
|---|---|---|
| 그냥 둔다 (세션 만료까지 기다림) | 간단 | 퇴사자·강등자가 최대 30분 더 사용 |
| 변경할 때 그 사용자의 세션을 찾아 만료 (SessionRegistry) | DB 조회 없음 | 세션 목록 관리가 필요, 서버 여러 대면 복잡 |
| **요청마다 DB 에서 다시 확인 (우리 선택)** | 단순하고 확실, 역할 변경까지 반영 | 요청마다 기본키 조회 1번 |

사내 시스템이라 요청 수가 많지 않아 세 번째를 택했습니다. `SessionUserRefreshFilter` 가 권한 검사 **직전에** DB 값을 읽어, 중지된 계정이면 세션을 끊고(401) 바뀐 점이 있으면(record `equals` 비교, 2부 9장) 세션을 새 값으로 바꿉니다. 이 비용이 실제로 얼마인지는 8부 41장에서 숫자로 확인합니다.

> 테스트: `UserAdminApiTest` 의 "비활성화하면_이미_로그인한_세션도_즉시_끊긴다", "역할을_낮추면_다음_요청부터_바로_권한이_줄어든다"

### 30.2. 세션 고정 공격 방지

로그인 성공 시 세션 ID 를 **새로 바꿉니다** (`SessionAuthenticator.signIn()` 의 `request.changeSessionId()`). 공격자가 미리 심어 둔 세션 ID 로 피해자가 로그인하게 만든 뒤 그 ID 를 쓰는 공격을 막습니다.

### 30.3. 이중 안전장치

`/api/v1/admin/**` 는 URL 규칙으로 이미 ADMIN 만 통과하지만, 컨트롤러 메서드에도 `@PreAuthorize("hasRole('ADMIN')")` 를 붙였습니다. 나중에 누가 URL 규칙을 바꾸거나 메서드 주소를 옮겨도 권한이 빠지지 않습니다. **한 곳만 믿지 않는다.**

### 30.4. 업무 규칙으로 막는 사고

`UserAdminService` 는 보안 사고를 **사람의 실수**에서도 막습니다.
- 관리자는 **본인**의 역할·사용 여부를 바꿀 수 없음 → 스스로 잠그는 것 방지
- 마지막 활성 관리자는 강등·중지 불가 → 아무도 관리할 수 없는 상태 방지
- 계정은 삭제하지 않고 중지 → 이력의 "누가" 가 사라지지 않음 (감사 추적)

### 30.5. 관리 기능 IP 제한과 CIDR

**무엇을 막나**: IP 제한을 켜면 `/api/v1/admin/**`(조회 포함)와 **데이터를 바꾸는 요청**(POST/PUT/PATCH/DELETE, 로그인 관련 제외)은 허용 IP 에서만 됩니다. GET 조회·다운로드는 어디서나 됩니다 (`IpRestrictionFilter.isRestricted()`).

**CIDR**: `192.168.0.0/24` = 앞 24비트(`192.168.0`)가 같은 주소 전부 = `192.168.0.0 ~ 192.168.0.255`. `/32` 는 IPv4 한 개, `/16` 은 65,536개. 사무실처럼 IP 가 범위 안에서 바뀌는 곳에 씁니다. 스프링 시큐리티의 `IpAddressMatcher` 가 IPv4·IPv6, 단일 IP·CIDR 을 모두 처리합니다.

**함정: 문자열을 IP 로 바꾸다가 DNS 를 조회한다**
`IpAddressMatcher` 는 내부에서 `InetAddress.getByName(문자열)` 을 씁니다. 이 메서드는 문자열이 IP 모양이 아니면 **호스트 이름으로 보고 DNS 조회**를 합니다. `"999.1.1.1"` 이나 `"office"` 를 허용 IP 로 입력하면, 검증하는 순간 서버가 DNS 서버에 물어보러 갑니다 — 느리고, 외부 DNS 결과에 따라 동작이 달라질 수 있습니다.
그래서 넘기기 전에 **글자 모양**으로 먼저 거릅니다 (`IpAccessPolicy.isIpLiteral()`: IPv4 는 숫자 4개·각 0~255, IPv6 는 16진수와 콜론만).
> 교훈: 라이브러리가 "문자열 → 객체" 변환에 실패하면 무엇을 하는지(예외? 네트워크 조회?) 확인하는 습관.

**localhost 와 IPv6**: Windows 에서 `http://localhost` 로 접속하면 서버가 보는 주소가 `127.0.0.1` 이 아니라 IPv6 `::1` 일 수 있습니다. 그래서 "서버 PC 자신" 판정에 `127.0.0.0/8` 과 `::1` 을 둘 다 넣었습니다. 이 "서버 PC 자신은 항상 허용" 이 허용 IP 를 잘못 지웠을 때의 **복구 경로**입니다.

**X-Forwarded-For 를 함부로 믿지 않기**: 서버 앞에 nginx 같은 프록시가 있으면 서버가 보는 IP 는 프록시의 IP 이고, 진짜 IP 는 `X-Forwarded-For` 헤더에 담겨 옵니다. 그런데 이 헤더는 **아무나 써서 보낼 수 있습니다**(1번 원칙). 그래서 `ClientIpResolver` 는 **설정한 신뢰 프록시에서 온 요청일 때만** 이 헤더를 읽습니다 (8부 42장).

> 자세히: [security-guide.md 10-1장](../security-guide.md), `docs/operations.md` 12장

### 정리

- 세션 사본은 요청마다 DB 와 맞춘다 (즉시 반영).
- 로그인 시 세션 ID 교체, URL + 메서드 이중 권한, 사람의 실수를 막는 업무 규칙.
- IP 는 글자 모양부터 검사하고, 프록시 헤더는 신뢰할 때만 읽는다.

### 확인 문제

30-1. `ClientIpResolver` 가 `X-Forwarded-For` 를 항상 믿는다면, 외부 공격자는 어떻게 IP 제한을 우회할 수 있을까요?

---

[← 4부](04-database-jpa.md) · [목차](../backend-concepts.md) · [6부. 파일 다루기 →](06-files-io.md)
