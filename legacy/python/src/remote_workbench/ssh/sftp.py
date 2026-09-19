from __future__ import annotations

import stat
from dataclasses import dataclass
from pathlib import Path

import paramiko


@dataclass(frozen=True)
class RemoteEntry:
    name: str
    path: str
    is_dir: bool
    size: int


class SftpClient:
    def __init__(self, sftp: paramiko.SFTPClient) -> None:
        self._sftp = sftp

    def listdir(self, remote_path: str) -> list[RemoteEntry]:
        entries: list[RemoteEntry] = []
        for attr in self._sftp.listdir_attr(remote_path):
            name = attr.filename
            full = f"{remote_path.rstrip('/')}/{name}"
            is_dir = stat.S_ISDIR(attr.st_mode or 0)
            entries.append(
                RemoteEntry(name=name, path=full, is_dir=is_dir, size=attr.st_size or 0)
            )
        entries.sort(key=lambda e: (not e.is_dir, e.name.lower()))
        return entries

    def upload(self, local_path: str | Path, remote_path: str) -> None:
        self._sftp.put(str(local_path), remote_path)

    def close(self) -> None:
        self._sftp.close()
