# RemoteUpdate Backend 보안 구조 설명

Controller · Service · Repository · Entity · DTO의 3계층은 안다는 전제에서, 그 **바깥**에서 동작하는 보안 장치를 설명합니다.
각 항목 끝에 실제 코드 위치를 적어 두었습니다.

---

## 1. 큰 그림: 요청은 Controller 전에 "필터 체인"을 지난다

3계층 구조에서는 요청이 바로 Controller로 들어오는 것처럼 보이지만, 실제로는 그 앞에 **필터**들이 줄지어 있습니다.
Spring Security는 이 필터들로 "누구인지", "들어와도 되는지"를 먼저 검사합니다. 통과하지 못하면 Controller는 호출조차 되지 않습니다.

```
브라우저 요청
   │
   ▼
┌───────────────────────── Spring Security 필터 체인 ─────────────────────────┐
│ ① 세션에서 로그인 정보 복원   (SecurityContext 를 세션에서 꺼냄)              │
│ ② CSRF 검사                  (POST/PUT/PATCH/DELETE 만)                      │
│ ③ 로그아웃 처리               (POST /api/v1/auth/logout 이면 여기서 끝)       │
│ ④ 권한 검사                   (URL 규칙: 로그인 필요? ADMIN 만?)              │
│ ⑤ 비밀번호 변경 강제          (우리가 만든 필터)                              │
└────────────────────────────────────────────────────────────────────────────┘
   │ 모두 통과
   ▼
DispatcherServlet → Controller → Service (@PreAuthorize 로 한 번 더 권한 검사 가능) → Repository
```

- 필터 단계에서 막히면 → `SecurityErrorWriter`가 JSON 오류를 직접 씀
- Controller 이후에서 예외가 나면 → `GlobalExceptionHandler`(@RestControllerAdvice)가 JSON 오류로 변환

오류 처리 클래스가 둘인 이유가 이것입니다. `@RestControllerAdvice`는 Controller 이후에서만 동작하고, 필터는 그보다 앞이라 잡지 못합니다.

> 코드: `config/security/SecurityConfig.java` (필터 체인 설정 전체)

---

## 2. 인증(Authentication)과 인가(Authorization)

| | 인증 | 인가 |
|---|---|---|
| 질문 | 너 누구야? | 이거 해도 돼? |
| 실패 시 | **401** Unauthorized (`UNAUTHORIZED`) | **403** Forbidden (`FORBIDDEN`) |
| 우리 프로젝트 | 이메일 + 비밀번호 로그인 → 세션 | 역할(STAFF / DEVELOPER / ADMIN) |

- 401 = 로그인 안 했음 → 프런트는 로그인 화면으로 보냄
- 403 = 로그인은 했지만 권한 없음 → "권한이 없습니다" 표시

---

## 3. 세션 + 쿠키 로그인

### 동작 방식

```
1) POST /api/v1/auth/login  { email, password }
2) 서버: 비밀번호 확인 → 서버 메모리에 세션 생성, 세션 안에 "로그인 사용자 정보(UserPrincipal)" 저장
3) 응답 헤더: Set-Cookie: JSESSIONID=ABC123; HttpOnly
4) 이후 모든 요청: 브라우저가 Cookie: JSESSIONID=ABC123 을 자동으로 붙여 보냄
5) 서버: ABC123 세션을 찾아 "이 요청은 고신원(ADMIN)" 으로 인식
```

- 서버는 세션 ID로 사용자를 기억합니다. 프런트 코드는 로그인 상태를 따로 저장할 필요가 없습니다.
- `HttpOnly` 쿠키라서 JavaScript가 읽을 수 없습니다. 페이지에 악성 스크립트가 들어와도 세션 ID를 훔쳐가기 어렵습니다.
- 세션은 30분 동안 요청이 없으면 만료됩니다 (`server.servlet.session.timeout`).

### JWT 대신 세션을 고른 이유 (기획서 결정 #6)

- 파일 다운로드를 `<a href>` 링크로 하면 브라우저가 쿠키를 자동으로 붙여 줍니다. JWT는 헤더에 직접 넣어야 해서 링크 다운로드가 어렵습니다.
- 로그아웃·계정 비활성화가 즉시 반영됩니다 (서버에서 세션을 지우면 끝).
- 서버 1대인 사내 시스템에는 세션이 더 단순합니다.

### 세션 고정(Session Fixation) 공격 방지

공격자가 미리 받아둔 세션 ID를 피해자에게 심어 두고, 피해자가 그 세션으로 로그인하면 공격자도 같은 세션을 쓰게 되는 공격입니다.
그래서 **로그인에 성공하는 순간 세션 ID를 새로 바꿉니다** (`request.changeSessionId()`).

> 코드: `auth/SessionAuthenticator.java`, `auth/AuthController.java`, `auth/UserPrincipal.java`

---

## 4. CSRF (Cross-Site Request Forgery)

### 어떤 공격인가

세션 쿠키는 **브라우저가 자동으로** 붙여 보낸다는 점을 노리는 공격입니다.

```
1) 직원이 RemoteUpdate 에 로그인한 상태 (브라우저에 JSESSIONID 쿠키 있음)
2) 같은 브라우저로 악성 사이트 evil.com 을 엶
3) evil.com 페이지에 숨겨진 코드:
     <form action="https://remoteupdate.사내/api/v1/updates/15/status" method="POST"> ... </form>
     <script>document.forms[0].submit()</script>
4) 브라우저는 remoteupdate 로 가는 요청이니 JSESSIONID 쿠키를 자동으로 붙임
5) 서버 입장에서는 "로그인한 직원의 정상 요청" → 업데이트가 비활성화됨
```

직원은 아무것도 누르지 않았는데, 직원 권한으로 상태 변경 요청이 실행됩니다.

### 어떻게 막는가: "쿠키 값을 헤더에 다시 써서 보내라"

```
1) 프런트 시작 시 GET /api/v1/auth/csrf
   → 응답: Set-Cookie: XSRF-TOKEN=무작위값   (HttpOnly 아님 → JS 가 읽을 수 있음)

2) 이후 POST/PUT/PATCH/DELETE 요청마다
   → 헤더: X-XSRF-TOKEN: 무작위값   (쿠키에서 읽어 복사. axios 가 자동으로 해 줌)

3) 서버: 쿠키 값 == 헤더 값 인지 비교. 다르거나 없으면 403 CSRF_INVALID
```

왜 이걸로 막히나요?

- evil.com 의 스크립트는 **우리 사이트의 쿠키 값을 읽을 수 없습니다** (브라우저의 동일 출처 정책).
- 쿠키는 브라우저가 자동으로 보내 주지만, **헤더는 자동으로 붙지 않습니다**.
- 그래서 공격자는 올바른 `X-XSRF-TOKEN` 헤더를 만들 수 없습니다.

### 규칙 두 가지

- **GET 요청은 CSRF 검사를 하지 않습니다.** 그러니 GET API에서는 절대 데이터를 바꾸면 안 됩니다 (REST 원칙과 같은 이야기).
  예외로, 다운로드 이력 기록은 GET에서 일어나지만 "조회에 따른 기록"이라 괜찮습니다.
- 토큰은 반드시 **쿠키 값**을 써야 합니다. `/auth/csrf` 응답 본문으로 토큰을 주지 않는 이유입니다 (서버 내부의 토큰 값은 다른 공격을 막기 위해 매번 마스킹되어 있어 쿠키 값과 모양이 다름).

추가 방어로 세션 쿠키에 `SameSite=Lax`를 걸어, 다른 사이트에서 오는 POST에는 세션 쿠키가 아예 붙지 않게 했습니다.

> 코드: `SecurityConfig` 의 `.csrf(csrf -> csrf.spa())`, `AuthController.csrf()`, Frontend `src/api/http.ts`
> 테스트: `CsrfCookieTest`

---

## 5. CORS와 Vite 프록시

브라우저는 **다른 출처**(프로토콜·도메인·포트 중 하나라도 다름)로 가는 요청을 기본적으로 막습니다. 이것이 CORS 제한입니다.

- 개발 중 프런트는 `localhost:5173`, 백엔드는 `localhost:8080` → 포트가 달라 다른 출처
- 원래라면 백엔드에 CORS 허용 설정과 쿠키 관련 추가 설정이 필요합니다.

우리는 Vite 개발 서버가 `/api` 요청을 8080으로 대신 전달(프록시)하게 했습니다.
브라우저는 모든 요청을 `5173`으로 보낸다고 생각하므로 같은 출처가 되고, CORS 설정이 필요 없습니다.
운영에서도 프런트와 백엔드를 같은 도메인 아래(예: Nginx로 `/`와 `/api` 분리)에 두면 같은 구조를 유지할 수 있습니다.

> 코드: Frontend `vite.config.ts` 의 `server.proxy`

---

## 6. 비밀번호 저장: BCrypt 해시

- DB에는 비밀번호 원문을 저장하지 않고 **BCrypt 해시**만 저장합니다 (`app_user.password_hash`).
- 해시는 되돌릴 수 없습니다. 로그인 시 입력값을 같은 방식으로 해시해 비교합니다 (`passwordEncoder.matches`).
- BCrypt는 해시마다 **무작위 salt**를 섞어서, 같은 비밀번호라도 해시 값이 매번 다릅니다. 미리 계산한 해시표로 역추적하는 공격이 통하지 않습니다.
- 일부러 **느리게**(약 0.1초) 계산되도록 설계돼 있어, DB가 유출돼도 대량 대입이 어렵습니다.

> 코드: `SecurityConfig.passwordEncoder()`, `AuthService`

---

## 7. 로그인 보호 장치

| 장치 | 내용 | 막는 공격 |
|---|---|---|
| 실패 잠금 | 5회 연속 실패 시 15분 잠금. 잠긴 동안은 비밀번호를 확인조차 하지 않음 | 비밀번호 무차별 대입 |
| 같은 오류 메시지 | 없는 계정과 틀린 비밀번호 모두 `INVALID_CREDENTIALS` | 이메일 존재 여부 탐색 |
| 같은 응답 시간 | 없는 계정이어도 가짜 해시로 BCrypt 비교를 수행 | 응답 시간 차이로 계정 존재 추측 |
| 비활성 안내 시점 | "사용 중지된 계정" 안내는 비밀번호가 맞았을 때만 | 계정 상태 탐색 |
| 첫 로그인 변경 강제 | 임시 비밀번호 상태에서는 `/api/v1/auth/**` 외 모든 API가 403 `PASSWORD_CHANGE_REQUIRED` | 관리자가 알려준 임시 비밀번호의 장기 사용 |

실패 횟수 기록에는 함정이 하나 있습니다. 로그인 실패 시 예외를 던지면 `@Transactional`이 **롤백**해서 실패 횟수 증가도 취소됩니다.
그래서 `@Transactional(noRollbackFor = ApiException.class)`로 예외가 나도 커밋되게 했습니다.

> 코드: `AuthService.authenticate()`, `user/AppUser.recordLoginFailure()`, `config/security/PasswordChangeRequiredFilter.java`

---

## 7-1. 계정 관리 보안 (Phase 5)

| 장치 | 내용 |
|---|---|
| 임시 비밀번호는 서버가 생성 | `SecureRandom`으로 12자, 응답에서 **한 번만** 보여주고 DB 에는 해시만 저장 → 관리자도 나중에 다시 볼 수 없음 |
| 첫 로그인 변경 강제 | 임시 비밀번호는 관리자가 알고 있으므로, 사용자가 바꾸기 전에는 다른 기능 사용 불가 |
| 권한 변경 즉시 반영 | 비활성화하면 로그인 중인 세션도 다음 요청에서 끊김, 역할을 낮추면 다음 요청부터 바로 적용 (`SessionUserRefreshFilter`) |
| 스스로 잠그기 방지 | 본인의 역할·사용 여부 변경 불가, 마지막 활성 관리자 강등·중지 불가 |
| 이메일 정규화 | 소문자로 통일해 `Admin@x.com`과 `admin@x.com`이 다른 계정으로 생기지 않게 |

## 8. 권한(역할) 검사

### 역할 계층

```
ADMIN  ⊃  DEVELOPER  ⊃  STAFF
```

`RoleHierarchy`를 등록해 두었기 때문에 "DEVELOPER 이상"을 `hasRole('DEVELOPER')` 하나로 표현할 수 있습니다. ADMIN도 자동으로 통과합니다.

### 검사하는 곳 두 군데

| 위치 | 방식 | 예 |
|---|---|---|
| URL 규칙 (필터) | `SecurityConfig.authorizeHttpRequests` | `/api/v1/admin/**` → ADMIN 만, 나머지 `/api/**` → 로그인 필요 |
| 메서드 (Controller) | `@PreAuthorize("hasRole('DEVELOPER')")` | 업데이트 등록은 DEVELOPER 이상 |

URL로 묶기 어려운 경우(같은 `/api/v1/projects` 인데 GET은 STAFF, POST는 ADMIN)는 메서드에 `@PreAuthorize`를 붙입니다.

### 코드에서 "로그인한 사용자" 꺼내기

```java
@GetMapping("/me")
public MeResponse me(@AuthenticationPrincipal UserPrincipal principal) {
    principal.id();    // 로그인 사용자 ID
    principal.role();  // 역할
}
```

요청 값으로 사용자 ID를 받지 않고 항상 세션의 사용자를 씁니다. 그래서 "담당 개발자"나 "다운로더"를 위조할 수 없습니다.

---

## 9. 설정과 비밀 값 관리

- DB 비밀번호는 코드나 설정 파일에 쓰지 않고 **환경 변수** `DB_PASSWORD`로 받습니다 (`application.yml`의 `${DB_PASSWORD:}`).
- 애플리케이션은 root가 아닌 **전용 계정** `remote_update`로 접속합니다. 이 계정은 `remote_update` DB에만 권한이 있어서, 혹시 SQL 인젝션 같은 문제가 생겨도 피해 범위가 그 DB로 한정됩니다.
- 시드 관리자 비밀번호 기본값(`ChangeMe!2026`)은 첫 로그인 때 바꾸도록 강제되므로 그대로 남지 않습니다.

---

## 10. 파일 업로드·다운로드와 IP 보안 장치

Phase 2·3 항목은 구현 완료 (`storage/LocalFileStorage.java`, `storage/UploadValidator.java`, `update/UpdateService.java`, `update/DownloadService.java`, `common/web/ClientIpResolver.java`).

| Phase | 장치 | 이유 |
|---|---|---|
| 2 | 업로드 확장자 화이트리스트, 크기 제한 | 실행 파일 위장, 디스크 고갈 |
| 2 | 저장 파일명을 서버가 UUID로 생성 | 파일명에 `../../` 를 넣어 서버의 다른 경로에 쓰는 **경로 조작** 공격 |
| 2 | SHA-256 체크섬, 파일 교체 불가 | 파일 손상·변조 확인 |
| 3 | 다운로드 시 권한·비활성 여부 확인 후 서버가 스트리밍 | 실제 저장 경로를 노출하지 않음 |
| 3 | 다운로드 이력의 IP는 `ClientIpResolver`로 결정. `X-Forwarded-For`는 신뢰하는 프록시(`app.security.ip-filter.trusted-proxies`)에서 온 경우에만 사용 | 헤더를 위조해 다른 IP로 기록되게 하는 공격 |
| 3 | 가이드 본문(마크다운)은 원문 그대로 내려가므로, 프런트에서 HTML로 바꿀 때 원시 HTML을 막아야 함 | 가이드에 스크립트를 심는 **XSS** 공격 |
| 7 | 허용 IP 필터 (같은 `ClientIpResolver` 사용) — 구현 완료, 10-1 참고 | 허용되지 않은 위치에서 관리 기능 사용 |

---

## 10-1. 관리 기능 IP 제한 (Phase 7)

로그인만으로는 비밀번호가 유출되면 어디서든 관리 기능을 쓸 수 있습니다. 그래서 **데이터를 바꾸는 요청**과 **관리자 메뉴**는 허용한 장소(사무실 IP)에서만 받습니다.

```
요청 → ... → 권한 검사(AuthorizationFilter) → IpRestrictionFilter → ...
                                                 │
                       /api/v1/admin/** 이거나, POST/PUT/PATCH/DELETE (단, /api/v1/auth/** 제외)?
                                 │ 예                                   │ 아니오
                     접속 IP 가 허용 목록(CIDR)에 있나?                     통과
                         │ 예          │ 아니오
                        통과        403 IP_NOT_ALLOWED
```

| 장치 | 이유 |
|---|---|
| 접속 IP 는 `ClientIpResolver` 하나로 결정, `X-Forwarded-For` 는 신뢰 프록시에서만 | 헤더 위조로 허용 IP 인 척하는 공격 차단 |
| 허용 목록은 단일 IP 와 CIDR(범위) 모두 | 사무실 IP 가 범위로 바뀌는 경우 |
| 입력값을 IP 모양인지 먼저 검사 | `InetAddress` 가 호스트 이름으로 보고 DNS 를 조회하는 것 방지 (backend-concepts.md 26번) |
| 서버 PC(127.0.0.1)는 항상 허용 | 설정 실수로 모두 잠겼을 때 복구 경로 |
| 지금 IP 를 막게 되는 변경 거부 | 원격 관리자가 스스로를 잠그는 사고 방지 |
| 로그인·조회·다운로드는 제한 안 함 | 현장 직원은 IP 가 수시로 바뀜 (기획서 결정 #4) |

> 코드: `ipfilter/IpRestrictionFilter.java`, `ipfilter/IpAccessPolicy.java`, `ipfilter/AllowedIpService.java`
> 운영 방법·긴급 절차: `docs/operations.md` 12번

## 11. XSS — 가이드 본문에 스크립트를 심는 공격

**어떤 공격인가**: 개발자 계정이 탈취되거나 실수로, 가이드 본문에 `<script>...</script>`가 들어가면 그 가이드를 연 **모든 직원의 브라우저에서** 스크립트가 실행됩니다. 직원 권한으로 API를 호출하거나 화면을 위조할 수 있습니다.

**막는 방법 (Frontend `src/utils/markdown.ts`)**
- 마크다운 변환기(markdown-it)를 `html: false`로 설정 → 본문의 HTML 태그는 실행되지 않고 **글자로** 보임
- `javascript:` 같은 위험한 링크는 링크로 만들지 않음 (markdown-it 기본 검사)
- 외부 링크는 `rel="noopener noreferrer"` → 열린 페이지가 우리 창을 조작하지 못함
- Vue의 `{{ }}`는 자동으로 이스케이프되므로 안전. **`v-html`은 위험**하니 `renderMarkdown()` 결과에만 사용

Phase 4 E2E 테스트에서 `<script>`, `<img onerror>`, `javascript:` 링크를 넣은 가이드를 열어 아무것도 실행되지 않는 것을 확인했습니다.

## 12. 직접 해보기 (서버 실행 후 PowerShell)

`.\gradlew.bat bootRun` 으로 서버를 띄운 뒤 다른 PowerShell 창에서 실행합니다.

```powershell
$base = 'http://localhost:8080/api/v1'
$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 1) CSRF 쿠키 받기
Invoke-WebRequest "$base/auth/csrf" -WebSession $s | Out-Null
$token = $s.Cookies.GetCookies('http://localhost:8080')['XSRF-TOKEN'].Value
$h = @{ 'X-XSRF-TOKEN' = $token }

# 2) 로그인 (시드 관리자) → mustChangePassword: true
Invoke-RestMethod "$base/auth/login" -Method Post -WebSession $s -Headers $h `
  -ContentType 'application/json' -Body '{"email":"admin@onpoom.co.kr","password":"ChangeMe!2026"}'

# 3) 비밀번호 바꾸기 전에는 다른 API 가 막힘 → 403 PASSWORD_CHANGE_REQUIRED
Invoke-RestMethod "$base/projects" -WebSession $s

# 4) CSRF 헤더 없이 POST → 403 CSRF_INVALID
Invoke-RestMethod "$base/auth/logout" -Method Post -WebSession $s
```

---

## 13. 파일 지도

| 파일 | 역할 |
|---|---|
| `config/security/SecurityConfig.java` | 필터 체인 전체 설정 (CSRF, URL 권한, 로그아웃, 오류 응답, 역할 계층, BCrypt) |
| `config/security/PasswordChangeRequiredFilter.java` | 임시 비밀번호 상태면 인증 API 외 차단 |
| `config/security/SecurityErrorWriter.java` | 필터 단계 오류를 JSON으로 작성 |
| `auth/AuthController.java` | `/api/v1/auth/*` (csrf, login, me, password) |
| `auth/AuthService.java` | 비밀번호 확인, 실패 잠금, 비밀번호 변경 |
| `auth/SessionAuthenticator.java` | 로그인 정보를 세션에 저장, 세션 ID 교체 |
| `auth/UserPrincipal.java` | 세션에 저장되는 로그인 사용자 (비밀번호 해시 미포함) |
| `auth/AdminSeeder.java` | 최초 관리자 계정 생성 |
| `common/error/*` | 오류 코드와 `{ code, message }` 응답 |
