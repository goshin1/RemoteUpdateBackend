# RemoteUpdate Backend

프로젝트별 **초기 세팅 가이드**와 **업데이트 파일**을 한곳에서 등록·배포하고, 누가 언제 무엇을 받았는지 기록하는 사내 시스템의 Backend(REST API)입니다.
현장 직원은 로그인해서 최신 파일을 직접 내려받고, 개발자는 업데이트를 등록·중단하며, 관리자는 계정과 접속 위치를 관리합니다.

> Frontend: [RemoteUpdateFrontend](https://github.com/goshin1/RemoteUpdateFrontend)

## 기술 스택

| 구분 | 사용 |
|---|---|
| 언어 / 런타임 | Java 17 |
| 프레임워크 | Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation) |
| ORM | Hibernate 7 |
| DB | MariaDB (테스트는 H2 MariaDB 모드) |
| 빌드 | Gradle 9 |
| 기타 | Lombok, Jackson 3 |

## 주요 기능

| 기능 | 내용 |
|---|---|
| 인증 | 세션 + 쿠키 로그인, CSRF 보호, 5회 실패 시 15분 잠금, 첫 로그인 비밀번호 변경 강제 |
| 권한 | 역할 계층 `ADMIN ⊃ DEVELOPER ⊃ STAFF`, 권한·계정 상태 변경이 로그인 중인 세션에도 즉시 반영 |
| 프로젝트 | 등록·수정 (관리자) |
| 업데이트 | 파일 업로드(최대 500MB, 확장자 제한), SHA-256 체크섬, 파일 교체 불가, 메타데이터 수정, 배포 중단·재개 |
| 다운로드 | 스트리밍 전송, 다운로드 이력(사용자·IP·시각) 자동 기록 |
| 이력 | 업데이트 변경 이력(변경 전/후 JSON), 다운로드 이력 검색(프로젝트·업데이트·사용자·기간) |
| 가이드 | 마크다운 본문 + 첨부 파일(교체 가능) |
| 사용자 관리 | 계정 발급(서버가 임시 비밀번호 생성), 역할 변경, 사용 중지, 비밀번호 초기화 — 삭제 없음 |
| IP 제한 | 데이터 변경·관리자 메뉴만 허용 IP(단일·CIDR)에서 사용 (선택 기능) |
| 운영 | 빌드된 Frontend 를 함께 제공(단일 서버), 관리자 복구 모드 |

## 빠른 시작 (개발)

### 1. 준비물

- JDK 17 이상
- MariaDB (로컬 설치)

### 2. DB 준비

PowerShell 에서 스크립트를 실행하면 DB·전용 계정을 만들고, 비밀번호를 사용자 환경 변수 `DB_PASSWORD` 에 저장합니다.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup-local-db.ps1
```

결과는 `build\db-check.txt` 에 남습니다. 새 환경 변수가 적용되도록 IDE·터미널을 다시 시작하세요.

<details>
<summary>직접 만들 경우</summary>

```sql
CREATE DATABASE remote_update CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'remote_update'@'localhost' IDENTIFIED BY '<비밀번호>';
GRANT ALL PRIVILEGES ON remote_update.* TO 'remote_update'@'localhost';
```

그리고 환경 변수 `DB_PASSWORD` 에 비밀번호를 설정합니다.
</details>

### 3. 실행

```powershell
.\gradlew.bat bootRun
```

- API: `http://localhost:8080/api/v1`
- 첫 실행 시 테이블이 자동 생성되고, 최초 관리자 계정이 만들어집니다.
  - 이메일 `admin@onpoom.co.kr` / 비밀번호 `ChangeMe!2026` (첫 로그인 때 변경 강제)
- 화면은 Frontend 저장소에서 `npm run dev` 로 띄웁니다 (`http://localhost:5173`, `/api` 는 8080 으로 프록시).

### 4. 테스트

```powershell
.\gradlew.bat test
```

MariaDB 없이 H2 로 실행됩니다 (`src/test/resources/application-test.yml`). 결과 보고서: `build/reports/tests/test/index.html`

## 설정 (환경 변수)

| 환경 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:mariadb://localhost:3306/remote_update` | |
| `DB_USERNAME` | `remote_update` | |
| `DB_PASSWORD` | (빈 값) | |
| `STORAGE_ROOT` | `./storage` | 업로드 파일 저장 위치 |
| `FRONTEND_DIST` | (빈 값) | 지정하면 빌드된 Frontend 를 이 서버가 함께 제공 |
| `COOKIE_SECURE` | `false` | HTTPS 운영 시 `true` |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@onpoom.co.kr` / `ChangeMe!2026` | 최초 관리자 (DB 에 관리자가 없을 때만 생성) |
| `ADMIN_RESET_ON_START` | `false` | 관리자 복구 모드 |
| `IP_FILTER_ENABLED` | `false` | 관리 기능 IP 제한 |
| `JPA_DDL_AUTO` | `update` | |

업로드 허용 확장자(`zip, exe, msi, pdf, md`)와 로그인 잠금 기준(5회 / 15분)은 `src/main/resources/application.yml` 의 `app.*` 에서 바꿉니다.

## 패키지 구조

```
com.onpoom.remoteupdate
├── auth        로그인·로그아웃·내 정보·비밀번호 변경, 세션 저장, 최초 관리자 생성
├── user        사용자 엔티티, 역할, 관리자의 사용자 관리
├── project     프로젝트
├── update      업데이트 등록·수정·상태 변경, 변경 이력, 다운로드·다운로드 이력
├── guide       초기 세팅 가이드
├── ipfilter    관리 기능 IP 제한, 허용 IP 관리
├── storage     파일 저장소(로컬 디스크), 업로드 검증
├── common      공통 엔티티(생성·수정 시각), 오류 응답, 페이징, 클라이언트 IP·파일 응답
└── config      설정값(AppProperties), JPA, 보안(필터 체인), 웹(Frontend 제공, 업로드 정책)
```

## API 요약

모든 응답은 JSON, 오류는 `{ "code": "...", "message": "..." }` 형식입니다. 상태 변경 요청에는 `X-XSRF-TOKEN` 헤더가 필요합니다 (`GET /auth/csrf` 로 쿠키 발급).

| Method | Endpoint | 권한 |
|---|---|---|
| GET | `/api/v1/auth/csrf` | 공개 |
| POST | `/api/v1/auth/login` · `/logout` | 공개 · 로그인 |
| GET / PUT | `/api/v1/auth/me` · `/auth/password` | 로그인 |
| GET | `/api/v1/projects`, `/projects/{id}` | 로그인 |
| POST / PUT | `/api/v1/projects`, `/projects/{id}` | ADMIN |
| GET | `/api/v1/projects/{id}/updates?status=&page=&size=` | 로그인 (직원은 배포 중만) |
| POST | `/api/v1/projects/{id}/updates` (multipart) | DEVELOPER |
| GET | `/api/v1/updates/{id}` · `/updates/{id}/download` | 로그인 |
| PUT / PATCH | `/api/v1/updates/{id}` · `/updates/{id}/status` | DEVELOPER |
| GET | `/api/v1/updates/{id}/history` | DEVELOPER |
| GET | `/api/v1/downloads?projectId=&updateId=&userId=&from=&to=` | DEVELOPER |
| GET / POST | `/api/v1/projects/{id}/guides` | 로그인 / DEVELOPER |
| GET / PUT | `/api/v1/guides/{id}` | 로그인 / DEVELOPER |
| GET / POST | `/api/v1/guides/{id}/attachment` | 로그인 / DEVELOPER |
| GET / POST / PUT / PATCH | `/api/v1/admin/users/**` (등록·수정·상태·비밀번호 초기화) | ADMIN |
| GET / POST / PATCH / DELETE | `/api/v1/admin/allowed-ips/**` | ADMIN |
| GET | `/api/v1/users/options`, `/api/v1/config/upload` | DEVELOPER |

## 문서

| 문서 | 내용 |
|---|---|
| [docs/operations.md](docs/operations.md) | 운영 배포·환경 변수·HTTPS·백업·관리자 복구·IP 제한 켜기와 긴급 절차 |
| [docs/security-guide.md](docs/security-guide.md) | 보안 구조 설명 (필터 체인, 세션, CSRF, BCrypt, XSS, IP 제한 등) |
| [docs/backend-concepts.md](docs/backend-concepts.md) | 이 프로젝트에서 쓴 Spring Boot / JPA 개념 노트 (공부용) |
| [docs/schema-mariadb.sql](docs/schema-mariadb.sql) | MariaDB 스키마 (참고용) |
| [Frontend docs/user-manual.md](https://github.com/goshin1/RemoteUpdateFrontend/blob/develop/docs/user-manual.md) | 화면 사용 설명서 (직원·개발자·관리자) |
