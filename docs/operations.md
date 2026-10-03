# RemoteUpdate 운영 가이드

개발이 끝난 뒤 실제로 띄우고, 백업하고, 문제가 생겼을 때 복구하는 방법입니다.
(개발 중 실행 방법은 루트의 `Development_Steps.md` "로컬 실행 방법" 참고)

---

## 1. 운영 구성

개발 중에는 Vite 개발 서버(5173)와 Spring(8080) 두 개를 띄웠지만, 운영에서는 **Spring 서버 하나**가 화면과 API를 모두 제공합니다.

```
브라우저 ──► (선택) HTTPS 리버스 프록시: Nginx / IIS ──► Spring Boot :8080
                                                        ├─ /api/**  → API (로그인·권한 검사)
                                                        ├─ /assets/** → Frontend 빌드 파일 (1년 캐시)
                                                        └─ 그 외 GET → index.html (Vue 라우터가 화면 처리)
                                                        │
                                       MariaDB ◄────────┤
                                       파일 저장소 ◄─────┘ (STORAGE_ROOT)
```

## 2. 빌드

```powershell
# Frontend → dist 폴더 생성
cd C:\Project\RemoteUpdate\Frontend\RemoteUpdate
npm ci
npm run build

# Backend → 실행 가능한 jar 생성 (build\libs\RemoteUpdateBackend-0.0.1-SNAPSHOT.jar)
cd C:\Project\RemoteUpdate\Backend\RemoteUpdateBackend
.\gradlew.bat bootJar
```

## 3. 실행

환경 변수로 설정하고 jar를 실행합니다. (Java 17 이상 필요)

```powershell
$env:DB_URL        = "jdbc:mariadb://localhost:3306/remote_update"
$env:DB_USERNAME   = "remote_update"
$env:DB_PASSWORD   = "<전용 계정 비밀번호>"
$env:STORAGE_ROOT  = "D:\RemoteUpdate\storage"                         # 업로드 파일 저장 위치 (절대 경로 권장)
$env:FRONTEND_DIST = "C:\Project\RemoteUpdate\Frontend\RemoteUpdate\dist"
$env:ADMIN_PASSWORD = "<최초 관리자 임시 비밀번호>"                      # DB 에 관리자가 없을 때만 사용됨
# HTTPS 로 운영할 때만
# $env:COOKIE_SECURE = "true"

java -jar build\libs\RemoteUpdateBackend-0.0.1-SNAPSHOT.jar
```

접속: `http://<서버 주소>:8080`

| 환경 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:mariadb://localhost:3306/remote_update` | |
| `DB_USERNAME` | `remote_update` | root 를 쓰지 말 것 |
| `DB_PASSWORD` | (빈 값) | `scripts/setup-local-db.ps1` 이 사용자 환경 변수로 저장해 둠 |
| `STORAGE_ROOT` | `./storage` | 실행 위치 기준 상대 경로 → 운영에서는 **절대 경로**로 |
| `FRONTEND_DIST` | (빈 값) | 비우면 화면을 제공하지 않음 (API 만) |
| `COOKIE_SECURE` | `false` | HTTPS 운영 시 `true` |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@onpoom.co.kr` / `ChangeMe!2026` | **운영에서는 반드시 비밀번호를 지정** |
| `ADMIN_RESET_ON_START` | `false` | 관리자 복구용 (7번) |
| `JPA_DDL_AUTO` | `update` | 6번 참고 |

> Windows 서비스로 등록하려면 WinSW, NSSM 같은 도구로 위 명령을 서비스로 감싸면 PC 재부팅 후에도 자동으로 실행됩니다.

## 4. HTTPS (권장)

현장 직원이 사내망 밖에서 접속한다면 HTTPS 가 필요합니다. 비밀번호와 세션 쿠키가 암호화 없이 오가면 가로챌 수 있습니다.

1. Nginx 또는 IIS 를 앞에 두고 인증서를 설정, 8080 으로 전달
2. `COOKIE_SECURE=true` → 세션 쿠키가 HTTPS 에서만 전송됨
3. 다운로드 이력에 실제 접속 IP 가 남도록 `application.yml` 의 `app.security.ip-filter.trusted-proxies` 에 프록시 주소(같은 PC 면 `127.0.0.1`)를 넣고, 프록시에서 `X-Forwarded-For` 를 붙이게 설정
4. 업로드 크기 제한을 프록시에도 맞춤 (Nginx: `client_max_body_size 510m;`)

## 5. 백업

데이터는 **두 곳**에 있고, 함께 백업해야 서로 맞습니다.

| 대상 | 내용 | 방법 |
|---|---|---|
| MariaDB `remote_update` | 사용자, 프로젝트, 업데이트 정보, 이력 | `mariadb-dump -u root -p remote_update > backup-2026-10-05.sql` |
| `STORAGE_ROOT` 폴더 | 실제 업데이트·가이드 파일 | 폴더 통째로 복사 |

- DB 만 복구하고 파일이 없으면 다운로드 시 "저장된 파일을 찾을 수 없습니다" 오류가 납니다.
- 업데이트 파일은 삭제하지 않는 정책이라 **디스크가 계속 늘어납니다.** 저장소 폴더 용량을 주기적으로 확인하세요.

## 6. DB 스키마 관리

지금은 `ddl-auto: update` 로, 서버가 시작할 때 엔티티에 맞춰 테이블·컬럼을 **추가**합니다.

- 편하지만 컬럼 이름 변경·삭제는 반영하지 않고, 의도치 않은 변경이 운영 DB 에 바로 들어갈 수 있습니다.
- 운영이 안정되면 `JPA_DDL_AUTO=validate`(엔티티와 테이블이 다르면 시작 실패)로 바꾸고, 스키마 변경은 Flyway 같은 마이그레이션 도구로 SQL 파일을 버전 관리하는 것을 권장합니다.
- 현재 스키마: `docs/schema-mariadb.sql`

## 7. 관리자 복구

관리자 비밀번호를 잊었거나, 관리자 계정이 잠기거나 중지된 경우:

```powershell
$env:ADMIN_RESET_ON_START = "true"
$env:ADMIN_PASSWORD = "<새 임시 비밀번호>"     # 영문+숫자 8자 이상
java -jar build\libs\RemoteUpdateBackend-0.0.1-SNAPSHOT.jar
```

1. 시작 로그에 `[복구] 관리자 계정을 초기화했습니다` 가 나오는지 확인
2. `ADMIN_EMAIL` 계정(기본 `admin@onpoom.co.kr`)으로 새 임시 비밀번호로 로그인 → 비밀번호 변경
3. **서버를 멈추고 `ADMIN_RESET_ON_START` 를 지운 뒤 다시 실행** (켜 둔 채 재시작하면 매번 초기화됨)

복구 모드는 그 계정을 ADMIN·사용 중 상태로 되돌리고, 로그인 잠금도 풉니다.

## 8. 로그

- 콘솔에 출력됩니다. 서비스로 등록했다면 서비스 도구의 로그 파일을 확인하세요.
- 기록되는 주요 이벤트: 로그인 실패(연속 횟수), 업데이트 등록·상태 변경, 다운로드(사용자·IP), 사용자 등록·중지·비밀번호 초기화, 복구 모드
- 비밀번호·임시 비밀번호·세션 ID 는 로그에 남기지 않습니다.

## 9. 새 버전 배포 순서

1. DB 와 저장소 백업 (5번)
2. Frontend `npm run build`, Backend `bootJar`
3. 서버 중지 → jar 교체 → 시작
4. 브라우저에서 로그인·목록·다운로드 확인 (index.html 은 캐시하지 않으므로 새 화면이 바로 보임)

## 10. 운영 전 점검 결과 (Phase 6, 2026-10-04)

| 항목 | 결과 |
|---|---|
| 실제 MariaDB(10.11)에서 테이블·제약·인덱스 생성 | 정상 (`docs/schema-mariadb.sql` 과 동일) |
| 한글 저장 (이름, JSON 이력) | 정상 (utf8mb4) |
| 전체 시나리오(관리자·개발자·직원) — 개발 방식(Vite) | 28개 확인 항목 통과 |
| 전체 시나리오 — 운영 방식(8080 단일 서버) | 28개 통과 (도중 버그 1건 발견·수정, 11번) |
| 490MB 업로드·다운로드, 힙 256MB 제한 | 업로드 5.4초, 다운로드 1.3초, 체크섬 일치, 프로세스 메모리 최대 약 300MB (파일을 메모리에 올리지 않음) |
| 510MB 업로드 | 413 `FILE_TOO_LARGE`, 임시 파일 남지 않음 |
| 요청당 SQL 수 | 2~3회 (목록이 길어져도 일정 — N+1 없음) |
| 관리자 복구 모드 | 정상 |

## 11. 운영 방식에서만 드러난 버그 (기록)

임시 비밀번호로 첫 로그인하면 "비밀번호 변경 필요" 필터가 **화면 파일(js, css)까지** 막아서 비밀번호 변경 화면을 띄울 수 없었습니다.
개발 중에는 화면 파일을 Vite 가 따로 제공해서 드러나지 않았습니다.
→ 필터를 `/api` 요청에만 적용하도록 수정하고, `FrontendServingTest` 에 회귀 테스트를 추가했습니다.
