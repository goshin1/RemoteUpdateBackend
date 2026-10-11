# 6부. 파일 다루기

[← 5부](05-security.md) · [목차](../backend-concepts.md) · [7부 →](07-testing.md)

RemoteUpdate 의 핵심은 결국 **파일을 받아 저장하고, 다시 내보내는 것**입니다. 그런데 파일은 크고(수백 MB), DB 밖에 있고(트랜잭션이 지켜 주지 않음), 이름에 위험한 글자가 들어올 수 있습니다. 이 부는 이 세 가지 어려움을 다룹니다.

---

## 31장. 자바 I/O 기초 — 스트림

### 이야기

500MB 파일을 `byte[] data = file.getBytes();` 로 읽으면 어떻게 될까요? 메모리에 500MB 가 통째로 올라갑니다. 10명이 동시에 올리면 5GB. 서버는 `OutOfMemoryError` 로 죽습니다.

### 개념: 스트림 = 물을 흘려보내는 관

**스트림(stream)** 은 데이터를 **조금씩 흘려보내는** 통로입니다. 한 번에 몇 KB 씩 읽고 쓰므로 파일 크기와 상관없이 메모리를 거의 쓰지 않습니다.

```java
try (InputStream in = file.getInputStream();          // 들어오는 관 (업로드된 파일)
     OutputStream out = Files.newOutputStream(temp)) { // 나가는 관 (디스크)
    long size = in.transferTo(out);                    // 조금씩 옮겨 담기 (내부적으로 버퍼 사용)
}
```

> 2부 7장의 Stream API(`list.stream().map()`)와 이름만 같고 다른 것입니다. 여기는 **바이트 흐름**인 I/O 스트림입니다.

### try-with-resources — 관은 반드시 잠근다

파일·네트워크 스트림은 다 쓰면 **닫아야(close)** 합니다. 안 닫으면 운영체제의 파일 핸들이 쌓여 결국 "파일을 열 수 없음" 오류가 나고, Windows 에서는 파일이 잠겨 지울 수도 없습니다.
`try (...)` 괄호 안에서 연 것은 **예외가 나도** 블록이 끝날 때 자동으로 닫힙니다. 스트림을 열 때는 항상 이 형태로 씁니다.

### 스트림 겹쳐 끼우기 — 흘려보내면서 해시 계산

파일을 다 저장한 뒤 SHA-256 을 구하려고 다시 읽으면 500MB 를 두 번 읽습니다. `DigestInputStream` 은 관 중간에 끼우는 **계량기**처럼, 지나가는 바이트로 해시를 동시에 계산합니다.

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
try (InputStream in = new DigestInputStream(content, digest);   // 관에 계량기를 끼움
     OutputStream out = Files.newOutputStream(temp)) {
    size = in.transferTo(out);
}
String sha256 = HexFormat.of().formatHex(digest.digest());       // 다 흘려보낸 뒤 결과 읽기
```

### 파일 시스템을 안전하게 다루는 습관 (`LocalFileStorage`)

| 습관 | 이유 | 코드 |
|---|---|---|
| **저장 이름은 서버가 만든다** (UUID) | 사용자가 준 이름(`../../config/application.yml`)으로 저장하면 엉뚱한 곳에 쓸 수 있음. 같은 이름 충돌도 없음 | `updates/2026/10/3f2a...` |
| **경로가 저장소 안인지 확인** | `root.resolve(key).normalize()` 결과가 `root` 로 시작하지 않으면 거부 → `..` 로 밖으로 나가는 공격(경로 조작) 차단 | `resolve()` |
| **임시 파일에 쓰고 마지막에 이동** | 저장 도중 실패하면 반쪽짜리 파일이 남음. 임시 파일에 다 쓴 뒤 `ATOMIC_MOVE` 로 한 번에 옮기면 "완전한 파일" 아니면 "없음" 둘 중 하나 | `createTempFile` → `Files.move(..., ATOMIC_MOVE)` |
| **원래 이름은 DB 에, 경로는 버림** | 다운로드할 때 보여 줄 이름만 필요. `C:\Users\...\a.zip` 처럼 경로가 붙어 와도 마지막 이름만 남김 | `UploadValidator.sanitizeFileName()` |
| **확장자 허용 목록** | 거부 목록(exe 금지)은 빠뜨리기 쉬움. **허용할 것만** 적음 | `app.upload.allowed-extensions` |

### 정리

- 큰 데이터는 스트림으로 조금씩. 스트림은 try-with-resources 로 반드시 닫는다.
- 관에 끼우는 스트림(DigestInputStream)으로 한 번 읽으며 여러 일을 한다.
- 저장 이름은 서버가, 경로는 저장소 안으로, 쓰기는 임시 파일 → 원자적 이동.

### 확인 문제

31-1. 업로드된 파일 이름을 그대로 저장 경로로 쓰면 `../../` 외에 어떤 문제가 더 생길까요? (힌트: 두 사람이 `setup.zip` 을 올리면?)

---

## 32장. 업로드와 다운로드

### 32.1. 업로드 = multipart/form-data

JSON 은 파일을 담을 수 없어서, 텍스트 필드와 파일을 함께 보내는 `multipart/form-data` 형식을 씁니다. 본문이 "경계선" 으로 나뉜 여러 부분(part)으로 이루어집니다.

```
------경계선
Content-Disposition: form-data; name="version"

1.1.0
------경계선
Content-Disposition: form-data; name="file"; filename="SmartControl_1.1.0.zip"
Content-Type: application/zip

(파일 바이트들...)
------경계선--
```

```java
@PostMapping(value = "/projects/{projectId}/updates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<UpdateResponse> create(
        @PathVariable Long projectId,
        @Valid @ModelAttribute UpdateCreateRequest request,                  // 텍스트 필드들 → record
        @RequestParam(value = "file", required = false) MultipartFile file,  // 파일 부분
        @AuthenticationPrincipal UserPrincipal principal)
```

- `@RequestBody`(JSON)가 아니라 `@ModelAttribute`(폼 필드)로 받습니다 (3부 16장).
- 파일을 `required = false` 로 받고 서비스에서 "파일을 선택하세요" 를 직접 검사합니다 — 오류 메시지를 우리 형식으로 주기 위해서입니다.
- 크기 제한은 `spring.servlet.multipart.max-file-size`(우리 500MB). **기본값이 1MB** 라서 반드시 늘려야 합니다. 넘으면 413 `FILE_TOO_LARGE`.
- `file-size-threshold: 2MB` — 그보다 큰 업로드는 메모리가 아니라 **임시 파일**로 받습니다 (31장의 메모리 문제).
- `server.tomcat.max-swallow-size: -1` — 크기 초과 시 톰캣이 연결을 끊어 버리지 않고 우리 오류 응답을 돌려줄 수 있게 합니다.

### 32.2. 다운로드 = 스트리밍 응답

파일 전체를 `byte[]` 로 올리지 않고, `Resource`(파일을 가리키는 객체)를 응답 본문으로 주면 스프링이 31장의 방식으로 조금씩 읽어 보냅니다.

```java
ContentDisposition disposition = ContentDisposition.attachment()
        .filename(fileName, StandardCharsets.UTF_8)      // 한글 파일 이름
        .build();
return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())   // "저장" 대화상자 + 파일 이름
        .contentType(MediaType.APPLICATION_OCTET_STREAM)                   // "그냥 바이트" (브라우저가 열지 않고 저장)
        .contentLength(size)                                               // 진행률 표시용
        .body(resource);
```

- `Content-Disposition: attachment; filename*=UTF-8''%EC%8A%A4...` — 한글 이름은 퍼센트 인코딩해서 보내야 브라우저가 깨지지 않게 표시합니다. 스프링의 `ContentDisposition` 이 이 형식을 만들어 줍니다.
- 500MB 파일을 10명이 동시에 받아도 서버 메모리는 거의 늘지 않습니다 (8부 41장에서 측정).

### 32.3. 트랜잭션 밖에서 보내기

다운로드에는 DB 작업(권한 확인, **다운로드 이력 저장**)과 파일 전송이 함께 있습니다. 전송은 느린 네트워크에서 몇 분 걸릴 수 있습니다. 이걸 한 트랜잭션에 넣으면 그동안 DB 연결을 붙잡아, 10명만 동시에 받아도 커넥션 풀(4부 21장)이 바닥납니다.

```
DownloadService.prepare()  ← @Transactional: 권한 확인 + 이력 저장 + DownloadTarget(파일 정보) 반환 → 커밋, 연결 반납
      ↓
DownloadController         ← 트랜잭션 밖: Resource 를 스트리밍으로 전송 (몇 분 걸려도 DB 연결 안 씀)
```

> 코드: `UpdateController.create()`, `LocalFileStorage.store()`, `DownloadService.prepare()`, `common/web/FileResponses`

### 32.4. 체크섬(SHA-256)으로 손상 확인

업로드 때 계산한 SHA-256 을 DB 에 저장하고 화면에 보여 줍니다. 현장 담당자가 받은 파일로 `Get-FileHash` 를 실행해 값이 같으면 **1바이트도 다르지 않다**는 뜻입니다. 1바이트만 달라도 해시는 완전히 달라집니다.
(5부 28장: 비밀번호에는 느린 BCrypt, 파일 확인에는 빠른 SHA-256 — 목적에 맞는 해시를 고릅니다.)

### 확인 문제

32-1. 다운로드 이력을 "전송이 끝난 뒤" 에 저장하면 더 정확할 것 같은데, 왜 전송 **전**에 저장할까요? (힌트: 트랜잭션과 연결, 그리고 "전송 끝" 을 서버가 확실히 알 수 있을까?)

---

## 33장. DB 밖의 일과 트랜잭션

### 이야기

업데이트 등록 순서는 "파일 저장 → DB 저장" 입니다. 파일은 디스크에 있어서 **DB 롤백이 되돌려 주지 않습니다**. DB 저장이 실패하면(예: 같은 버전 동시 등록) 디스크에 **주인 없는 파일**이 남습니다. 반복되면 디스크가 쓰레기로 찹니다.

### 개념: "트랜잭션이 끝나면 이걸 해 줘"

스프링은 트랜잭션의 결과에 맞춰 실행할 일을 등록하는 방법을 제공합니다.

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

**반대 상황 — 가이드 첨부 교체**: 새 파일 저장 → DB 가 새 파일을 가리키게 수정 → **이전 파일 삭제**. 이전 파일을 바로 지웠는데 DB 가 롤백되면, DB 는 이전 파일을 가리키는데 파일은 이미 없습니다. 그래서 이전 파일 삭제는 `afterCommit()`(커밋이 **확정된 뒤**)에 합니다.

| 상황 | 언제 | 무엇을 |
|---|---|---|
| 새 파일 저장 후 DB 실패 | `afterCompletion` + 롤백이면 | 새 파일 삭제 |
| 이전 파일 교체 | `afterCommit` | 이전 파일 삭제 |

### 원칙

DB 밖의 부수 효과(파일, 메일 발송, 외부 API 호출)는 **트랜잭션이 되돌려 주지 않는다.** 그러니
- 되돌릴 수 없는 일은 **커밋이 확정된 뒤**에 하고,
- 먼저 해야 하는 일은 **실패 시 되돌릴 방법**을 마련한다.

> 코드: `UpdateService.deleteFileIfRolledBack()`, `GuideService.store()`, `GuideService.afterCommit()`
> 같은 고민이 2차 개발에도 나옵니다: MariaDB 의 테이블 변경(DDL)은 롤백되지 않으므로 **업데이트 전 덤프 백업**을 둡니다 (4부 26장).

### 정리

- 트랜잭션은 DB 만 지킨다. 파일·메일·외부 호출은 직접 챙긴다.
- `afterCompletion`(롤백 시 정리), `afterCommit`(확정 후 실행)으로 DB 결과에 맞춘다.

### 확인 문제

33-1. "업데이트가 등록되면 개발팀에 메일 보내기" 기능을 추가한다면, 메일은 트랜잭션 안에서 보내야 할까요, `afterCommit` 에서 보내야 할까요?

---

[← 5부](05-security.md) · [목차](../backend-concepts.md) · [7부. 테스트 →](07-testing.md)
