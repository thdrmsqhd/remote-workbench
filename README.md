# remote-workbench

원격 작업 때 SSH·파일전송·멀티탭이 도구마다 흩어지는 문제를, **SSH 셸 + 탭 + SFTP 파일트리** 최소 POC로 증명합니다. (풀 MobaXterm 클론 아님)

## 뭘 만들었나

| 포함 | 제외 |
|------|------|
| SSH 인터랙티브 셸 | X11 · 시리얼 · RDP/VNC |
| 탭 2개 이상 | 매크로 · 클라우드 세션 동기화 |
| SFTP 목록 · 업로드 | FTP 프로토콜 |
| SFTP 디렉터리 탐색(파일트리) | 풀 세션 매니저 |

스택: **Python 3.11+ · paramiko · Textual**

## 실행 3줄

```bash
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env   # 로컬 테스트 호스트만 입력
remote-workbench
```

## 데모

1. `.env`에 자기 테스트 SSH 호스트 설정 (회사 계정 금지)
2. 앱 실행 → 탭 2개에서 각각 명령 입력
3. 왼쪽 파일트리로 디렉터리 이동 → 로컬 파일 업로드

## 테스트

```bash
pytest -q
```

## 포트폴리오 한 줄

접속→셸→파일 이동을 한 화면에서 직접 구현한 1인 POC.
