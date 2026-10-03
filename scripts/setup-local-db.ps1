<#
  RemoteUpdate 로컬 MariaDB 점검 및 설정

  하는 일
    1. MariaDB 클라이언트(mariadb.exe / mysql.exe) 찾기
    2. root 로 TCP(127.0.0.1:3306) 접속 확인, 서버 설정 점검
    3. remote_update 데이터베이스(utf8mb4) 생성
    4. 애플리케이션 전용 계정 remote_update 생성(또는 비밀번호 재설정) + 권한 부여
    5. 전용 계정으로 접속 테스트
    6. 전용 계정 비밀번호를 사용자 환경 변수 DB_PASSWORD 에 저장 (application.yml 이 읽음)
    7. 결과를 build\db-check.txt 에 기록 (비밀번호는 기록하지 않음)

  실행 (Backend\RemoteUpdateBackend 폴더에서)
    powershell -ExecutionPolicy Bypass -File scripts\setup-local-db.ps1

  여러 번 실행해도 됩니다. 실행할 때마다 전용 계정 비밀번호가 새로 만들어집니다.
#>
param(
    [string]$RootPassword,
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 3306,
    [string]$Database = 'remote_update',
    [string]$AppUser = 'remote_update'
)

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $projectDir 'build'
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
$report = Join-Path $buildDir 'db-check.txt'
$lines = New-Object System.Collections.Generic.List[string]

function Log([string]$text) {
    Write-Host $text
    $lines.Add($text)
}

function Save-Report {
    [System.IO.File]::WriteAllLines($report, $lines, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host ""
    Write-Host "결과 파일: $report"
}

function Invoke-Sql([string]$user, [string]$password, [string]$sql, [string]$db = '') {
    $env:MYSQL_PWD = $password
    $cliArgs = @('-u', $user, '-h', $DbHost, '-P', "$Port", '--protocol=TCP', '--batch', '--skip-column-names',
              '--default-character-set=utf8mb4')
    if ($db) { $cliArgs += @('-D', $db) }
    $cliArgs += @('-e', $sql)
    # Windows PowerShell 5.1 은 Stop 상태에서 네이티브 명령의 stderr 를 예외로 바꾸므로 잠시 Continue
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $out = & $script:client @cliArgs 2>&1
    $ErrorActionPreference = $prev
    $code = $LASTEXITCODE
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    if ($code -ne 0) { throw ($out | Out-String).Trim() }
    return ($out | Where-Object { "$_" -notmatch 'Using a password on the command line|^WARNING:' } | ForEach-Object { "$_" })
}

Log "== RemoteUpdate 로컬 DB 점검 ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))"

# 1. 클라이언트 찾기
$candidates = @()
foreach ($name in 'mariadb.exe', 'mysql.exe') {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += $cmd.Source }
}
foreach ($base in @($env:ProgramFiles, ${env:ProgramFiles(x86)})) {
    if ($base -and (Test-Path $base)) {
        $candidates += Get-ChildItem -Path $base -Directory -Filter 'MariaDB*' -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName 'bin\mariadb.exe'; Join-Path $_.FullName 'bin\mysql.exe' } |
            Where-Object { Test-Path $_ }
    }
}
$script:client = $candidates | Select-Object -First 1
if (-not $script:client) {
    Log "[실패] MariaDB 클라이언트(mariadb.exe)를 찾지 못했습니다. 설치 경로의 bin 폴더를 PATH 에 추가하세요."
    Save-Report; exit 1
}
Log "[확인] 클라이언트: $script:client"

$svc = Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'maria|mysql' -or $_.DisplayName -match 'MariaDB' }
foreach ($s in $svc) { Log "[확인] 서비스: $($s.Name) / 상태 $($s.Status) / 시작 유형 $($s.StartType)" }

# 2. root 접속
if (-not $RootPassword) {
    $secure = Read-Host 'MariaDB root 비밀번호' -AsSecureString
    $RootPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
try {
    $version = Invoke-Sql 'root' $RootPassword 'SELECT VERSION();'
    Log "[확인] root TCP 접속 성공 ($DbHost`:$Port), 서버 버전: $version"
} catch {
    Log "[실패] root 접속 실패: $_"
    Save-Report; exit 1
}

Log ""
Log "== 서버 설정"
$vars = Invoke-Sql 'root' $RootPassword "SHOW GLOBAL VARIABLES WHERE Variable_name IN ('character_set_server','collation_server','time_zone','system_time_zone','default_storage_engine','lower_case_table_names','max_allowed_packet','sql_mode','port','bind_address');"
foreach ($v in $vars) { Log "  $v" }

# 3. 데이터베이스
Log ""
Log "== 데이터베이스 / 계정 설정"
Invoke-Sql 'root' $RootPassword "CREATE DATABASE IF NOT EXISTS ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" | Out-Null
$dbInfo = Invoke-Sql 'root' $RootPassword "SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$Database';"
Log "[확인] 데이터베이스 $Database : $dbInfo"

# 4. 전용 계정 (localhost / 127.0.0.1 / ::1 모두)
$chars = [char[]]((48..57) + (65..90) + (97..122))
$appPassword = -join (1..24 | ForEach-Object { $chars | Get-Random })
foreach ($h in 'localhost', '127.0.0.1', '::1') {
    Invoke-Sql 'root' $RootPassword "CREATE USER IF NOT EXISTS '$AppUser'@'$h' IDENTIFIED BY '$appPassword'; ALTER USER '$AppUser'@'$h' IDENTIFIED BY '$appPassword'; GRANT ALL PRIVILEGES ON ``$Database``.* TO '$AppUser'@'$h';" | Out-Null
}
Invoke-Sql 'root' $RootPassword 'FLUSH PRIVILEGES;' | Out-Null
Log "[확인] 전용 계정 $AppUser 생성/비밀번호 갱신 (localhost, 127.0.0.1, ::1)"
$grants = Invoke-Sql 'root' $RootPassword "SHOW GRANTS FOR '$AppUser'@'localhost';"
foreach ($g in $grants) { Log "  $($g -replace "IDENTIFIED BY PASSWORD '[^']*'", "IDENTIFIED BY PASSWORD '***'")" }

# 5. 전용 계정 접속 테스트 (테이블 생성/삭제까지)
try {
    $who = Invoke-Sql $AppUser $appPassword 'SELECT CURRENT_USER(), DATABASE();' $Database
    Invoke-Sql $AppUser $appPassword 'CREATE TABLE IF NOT EXISTS _setup_check (id INT); DROP TABLE _setup_check;' $Database | Out-Null
    Log "[확인] 전용 계정 접속 및 테이블 생성 권한 OK: $who"
} catch {
    Log "[실패] 전용 계정 접속 실패: $_"
    Save-Report; exit 1
}

$tables = Invoke-Sql 'root' $RootPassword "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$Database' ORDER BY TABLE_NAME;"
if ($tables) { Log "[확인] 현재 테이블: $($tables -join ', ')" } else { Log "[확인] 현재 테이블 없음 (서버 첫 실행 시 자동 생성)" }

# 6. 환경 변수
[Environment]::SetEnvironmentVariable('DB_PASSWORD', $appPassword, 'User')
Log ""
Log "[확인] 사용자 환경 변수 DB_PASSWORD 저장 완료 (값은 기록하지 않음)"
Log "       DB_URL / DB_USERNAME 은 application.yml 기본값(jdbc:mariadb://localhost:3306/$Database, $AppUser)을 사용"
Log "       ※ IntelliJ 와 터미널을 다시 시작해야 새 환경 변수가 적용됩니다."
Log ""
Log "== 완료"
Save-Report
