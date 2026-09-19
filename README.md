# remote-workbench

원격 작업 때 SSH·파일전송·멀티탭이 흩어지는 문제를, **SSH 셸 + 탭 + SFTP 파일트리** 최소 POC로 증명합니다. (풀 MobaXterm 클론 아님)

**현재 스택: Kotlin · Compose Desktop · JSch**  
(이전 Python POC는 `legacy/python/`에 보관)

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
