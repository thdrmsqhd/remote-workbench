# remote-workbench

원격 작업 때 SSH·파일전송·멀티탭이 흩어지는 문제를, **SSH 셸 + 탭 + SFTP 파일트리** 최소 POC로 증명합니다. (풀 MobaXterm 클론 아님)

**현재 스택: Kotlin · Compose Desktop · JSch**  
(이전 Python POC는 `legacy/python/`에 보관)


## 다운로드 (릴리즈)

GitHub Releases: https://github.com/thdrmsqhd/remote-workbench/releases

| 파일 | 용도 |
|------|------|
| `remote-workbench-*.exe` / `*.msi` | **Windows** — Actions가 릴리즈에 첨부 (Actions → Release Windows 수동 실행 가능) |
| `remote-workbench-linux-x64-*.jar` | Linux — Java 21+ 에서 `java -jar …` |
| `remote-workbench-linux-x64-*.tar.gz` | Linux x64 앱 번들 |

Windows exe는 `windows-latest` GitHub Actions에서 빌드됩니다. 태그 `v*` push 또는 Release 발행 시 자동, 또는 Actions에서 **Release Windows** 워크플로를 수동 실행하세요.

## 인수조건

1. SSH 셸 입출력
2. 탭 2개 이상(독립 세션)
3. SFTP 목록 · 업로드
4. 디렉터리 탐색(파일트리)

FTP · 화면 분할 · X11 · 시리얼 · RDP/VNC 제외.

## 실행

```bash
cp .env.example .env   # 로컬 테스트 호스트만
./gradlew run
```

## 테스트

```bash
./gradlew test
```

## 모듈

```
src/main/kotlin/workbench/
  config/   # SSH 설정
  ssh/      # 세션·셸
  sftp/     # 목록·업로드·트리용 경로
  ui/       # 탭 · 파일트리 · 셸 패널
legacy/python/   # 이전 Python POC
```
