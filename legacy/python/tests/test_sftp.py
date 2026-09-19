from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import MagicMock

import paramiko

from remote_workbench.ssh.sftp import SftpClient


def _attr(name: str, is_dir: bool, size: int = 0):
    mode = 0o040755 if is_dir else 0o100644
    return SimpleNamespace(filename=name, st_mode=mode, st_size=size)


def test_listdir_sorts_dirs_first():
    sftp = MagicMock()
    sftp.listdir_attr.return_value = [
        _attr("b.txt", False, 10),
        _attr("a_dir", True),
        _attr("c.txt", False, 2),
    ]
    client = SftpClient(sftp)
    entries = client.listdir("/tmp")
    assert [e.name for e in entries] == ["a_dir", "b.txt", "c.txt"]
    assert entries[0].is_dir is True
    assert entries[0].path == "/tmp/a_dir"


def test_upload_calls_put():
    sftp = MagicMock()
    client = SftpClient(sftp)
    client.upload("/local/a.txt", "/remote/a.txt")
    sftp.put.assert_called_once_with("/local/a.txt", "/remote/a.txt")
