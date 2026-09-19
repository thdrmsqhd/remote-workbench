import os

import pytest

from remote_workbench.config import load_config


def test_load_config_requires_host(monkeypatch):
    monkeypatch.delenv("SSH_HOST", raising=False)
    monkeypatch.setenv("SSH_PASSWORD", "x")
    with pytest.raises(ValueError, match="SSH_HOST"):
        load_config()


def test_load_config_ok(monkeypatch):
    monkeypatch.setenv("SSH_HOST", "127.0.0.1")
    monkeypatch.setenv("SSH_USER", "demo")
    monkeypatch.setenv("SSH_PASSWORD", "secret")
    monkeypatch.setenv("SSH_PORT", "2222")
    cfg = load_config()
    assert cfg.host == "127.0.0.1"
    assert cfg.port == 2222
    assert cfg.username == "demo"
