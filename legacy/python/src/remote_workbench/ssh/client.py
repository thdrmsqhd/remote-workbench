from __future__ import annotations

from typing import Callable

import paramiko

from remote_workbench.config import SshConfig


class SshSession:
    """한 탭당 하나의 SSH 세션."""

    def __init__(self, config: SshConfig) -> None:
        self._config = config
        self._client: paramiko.SSHClient | None = None
        self._channel: paramiko.Channel | None = None

    def connect(self) -> None:
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        kwargs: dict = {
            "hostname": self._config.host,
            "port": self._config.port,
            "username": self._config.username,
            "allow_agent": False,
            "look_for_keys": False,
        }
        if self._config.key_path:
            kwargs["key_filename"] = self._config.key_path
        if self._config.password:
            kwargs["password"] = self._config.password
        client.connect(**kwargs)
        channel = client.invoke_shell(term="xterm", width=120, height=40)
        channel.settimeout(0.0)
        self._client = client
        self._channel = channel

    @property
    def connected(self) -> bool:
        return self._channel is not None and not self._channel.closed

    def send(self, data: str) -> None:
        if not self._channel:
            raise RuntimeError("SSH 세션이 연결되어 있지 않습니다.")
        self._channel.send(data)

    def recv(self, size: int = 4096) -> str:
        if not self._channel:
            return ""
        if self._channel.recv_ready():
            return self._channel.recv(size).decode(errors="replace")
        return ""

    def close(self) -> None:
        if self._channel:
            self._channel.close()
            self._channel = None
        if self._client:
            self._client.close()
            self._client = None

    def open_sftp(self) -> paramiko.SFTPClient:
        if not self._client:
            raise RuntimeError("SSH 세션이 연결되어 있지 않습니다.")
        return self._client.open_sftp()
