from __future__ import annotations

import os
from dataclasses import dataclass

from dotenv import load_dotenv


@dataclass(frozen=True)
class SshConfig:
    host: str
    port: int
    username: str
    password: str | None
    key_path: str | None
    remote_dir: str


def load_config() -> SshConfig:
    load_dotenv()
    host = os.getenv("SSH_HOST", "").strip()
    if not host:
        raise ValueError("SSH_HOST가 비어 있습니다. .env.example을 참고해 .env를 만드세요.")
    password = os.getenv("SSH_PASSWORD") or None
    key_path = os.getenv("SSH_KEY_PATH") or None
    if not password and not key_path:
        raise ValueError("SSH_PASSWORD 또는 SSH_KEY_PATH 중 하나가 필요합니다.")
    return SshConfig(
        host=host,
        port=int(os.getenv("SSH_PORT", "22")),
        username=os.getenv("SSH_USER", "").strip() or "demo",
        password=password,
        key_path=key_path,
        remote_dir=os.getenv("SSH_REMOTE_DIR", "/tmp"),
    )
