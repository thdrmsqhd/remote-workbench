# remote-workbench

원격 작업 때 SSH·파일전송·멀티탭이 흩어지는 문제를, **SSH 셸 + 탭 + SFTP 파일트리** 최소 POC로 증명합니다. (풀 MobaXterm 클론 아님)

**현재 스택: Kotlin · Compose Desktop · JSch · SQLite**  
(이전 Python POC는 `legacy/python/`에 보관)


## 다운로드 (릴리즈)

GitHub Releases: https://github.com/thdrmsqhd/remote-workbench/releases

| 파일 | 용도 |
|------|------|
| `remote-workbench-*.exe` / `*.msi` | **Windows** — Actions가 릴리즈에 첨부 (Actions → Release Windows 수동 실행 가능) |
| `remote-workbench-linux-x64-*.jar` | Linux — Java 21+ 에서 `java -jar …` |
| `remote-workbench-linux-x64-*.tar.gz` | Linux x64 앱 번들 |

Windows exe는 `windows-latest` GitHub Actions에서 빌드됩니다. 태그 `v*` push 또는 Release 발행 시 자동, 또는 Actions에서 **Release Windows** 워크플로를 수동 실행하세요.

## 주요 기능

1. **SSH 셸 입출력**: xterm PTY 기반 실시간 터미널 입출력
2. **멀티탭 및 화면 분할(Split View)**: 단일 탭 모드 및 2개 독립 세션을 나란히 띄워 모니터링 및 동시 작업 가능한 2분할 뷰 지원
3. **SFTP 파일트리 & 드래그 앤 드롭**: 디렉터리 시각적 탐색 및 데스크톱 파일을 탐색기로 드래그 앤 드롭하여 원격 디렉터리에 즉시 업로드
4. **SQLite 서버 프로필 관리**: 로컬 SQLite DB(`~/.remote-workbench/workbench.db`)에 다중 서버 접속 정보 안전 저장 및 관리 (기존 `.env` 자동 임포트 지원)

## 실행

```bash
./gradlew run
```

## 테스트

```bash
./gradlew test
```

## 모듈

```
src/main/kotlin/workbench/
  config/   # SSH 레거시 .env 설정 로더
  data/     # SQLite DB 매니저 및 서버 프로필 저장소
  ssh/      # JSch 기반 세션·셸 제어
  sftp/     # SFTP 파일 목록 조회 및 파일 업로드
  ui/       # Compose Desktop UI (툴바, 탭, 2분할 뷰, 파일트리, 드래그앤드롭, 터미널)
legacy/python/   # 이전 Python POC
```
