from __future__ import annotations

from pathlib import Path

from textual.containers import Horizontal, Vertical
from textual.widgets import Button, Input, Label, ListItem, ListView, Static, TabPane, TextArea

from remote_workbench.config import SshConfig
from remote_workbench.ssh.client import SshSession
from remote_workbench.ssh.sftp import RemoteEntry, SftpClient


class SessionTab(TabPane):
    def __init__(self, title: str, config: SshConfig, tab_id: str) -> None:
        super().__init__(title, id=tab_id)
        self._config = config
        self.session = SshSession(config)
        self.sftp: SftpClient | None = None
        self._cwd = config.remote_dir

    def compose(self):
        with Horizontal():
            with Vertical(id="side"):
                yield Label("", id="cwd-label")
                yield ListView(id="remote-list")
                yield Input(placeholder="업로드할 로컬 파일 경로", id="upload-path")
                yield Button("업로드", id="upload-btn", variant="primary")
            yield TextArea(id="shell", read_only=False)

    def on_mount(self) -> None:
        self.session.connect()
        self.sftp = SftpClient(self.session.open_sftp())
        self._refresh_listing()
        self.set_interval(0.1, self._poll_shell)
        shell = self.query_one("#shell", TextArea)
        shell.show_line_numbers = False
        shell.insert(f"연결됨: {self._config.username}@{self._config.host}\n")

    def _poll_shell(self) -> None:
        data = self.session.recv()
        if data:
            self.query_one("#shell", TextArea).insert(data)

    def _refresh_listing(self) -> None:
        if not self.sftp:
            return
        self.query_one("#cwd-label", Label).update(f"원격: {self._cwd}")
        lv = self.query_one("#remote-list", ListView)
        lv.clear()
        lv.append(ListItem(Label(".."), id="entry-parent"))
        try:
            entries = self.sftp.listdir(self._cwd)
        except OSError as exc:
            lv.append(ListItem(Label(f"(오류: {exc})")))
            return
        for entry in entries:
            mark = "/" if entry.is_dir else ""
            item = ListItem(Label(f"{entry.name}{mark}"))
            item.entry = entry  # type: ignore[attr-defined]
            lv.append(item)

    def on_list_view_selected(self, event: ListView.Selected) -> None:
        item = event.item
        if item.id == "entry-parent":
            parent = str(Path(self._cwd).parent)
            self._cwd = parent if parent else "/"
            self._refresh_listing()
            return
        entry: RemoteEntry | None = getattr(item, "entry", None)
        if entry and entry.is_dir:
            self._cwd = entry.path
            self._refresh_listing()

    def on_text_area_changed(self, event: TextArea.Changed) -> None:
        # 입력은 on_key에서 처리
        pass

    def on_key(self, event) -> None:  # type: ignore[no-untyped-def]
        if self.focused and getattr(self.focused, "id", None) == "shell":
            key = event.key
            if key == "enter":
                self.session.send("\r")
                event.prevent_default()
            elif key == "backspace":
                self.session.send("\x7f")
                event.prevent_default()
            elif event.character and len(event.character) == 1:
                self.session.send(event.character)
                event.prevent_default()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        if event.button.id != "upload-btn" or not self.sftp:
            return
        local = self.query_one("#upload-path", Input).value.strip()
        if not local:
            self.notify("로컬 경로를 입력하세요", severity="warning")
            return
        name = Path(local).name
        remote = f"{self._cwd.rstrip('/')}/{name}"
        try:
            self.sftp.upload(local, remote)
            self._refresh_listing()
            self.notify(f"업로드됨: {remote}")
        except OSError as exc:
            self.notify(f"업로드 실패: {exc}", severity="error")

    def on_unmount(self) -> None:
        if self.sftp:
            self.sftp.close()
        self.session.close()
