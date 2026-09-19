from __future__ import annotations

from textual.app import App, ComposeResult
from textual.binding import Binding
from textual.widgets import Footer, Header, TabbedContent

from remote_workbench.config import load_config
from remote_workbench.ui.tabs import SessionTab


class RemoteWorkbenchApp(App):
    CSS = """
    #side { width: 36; border-right: solid $accent; }
    #shell { height: 1fr; }
    #remote-list { height: 1fr; }
    """
    TITLE = "remote-workbench"
    BINDINGS = [
        Binding("ctrl+n", "new_tab", "새 탭"),
        Binding("ctrl+q", "quit", "종료"),
    ]

    def __init__(self) -> None:
        super().__init__()
        self._config = load_config()
        self._tab_n = 0

    def compose(self) -> ComposeResult:
        yield Header()
        with TabbedContent(id="tabs"):
            yield self._make_tab()
            yield self._make_tab()
        yield Footer()

    def _make_tab(self) -> SessionTab:
        self._tab_n += 1
        return SessionTab(f"세션 {self._tab_n}", self._config, tab_id=f"tab-{self._tab_n}")

    def action_new_tab(self) -> None:
        tabs = self.query_one("#tabs", TabbedContent)
        pane = self._make_tab()
        tabs.add_pane(pane)


def main() -> None:
    RemoteWorkbenchApp().run()


if __name__ == "__main__":
    main()
