#!/usr/bin/env python3
"""Small GTK4 window for sending global chat lines as the admin.

Lines are appended to Server/admin-chat.spool (override with ADMIN_CHAT_SPOOL);
the running server polls that file once a second and broadcasts each line.
Needs a server built with the spool poller and enable_global_chat = true.
"""
import os
import time
import gi

gi.require_version("Gtk", "4.0")
from gi.repository import Gtk  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPOOL = os.environ.get("ADMIN_CHAT_SPOOL", os.path.join(ROOT, "Server", "admin-chat.spool"))


class App(Gtk.Application):
    def __init__(self):
        super().__init__(application_id="dev.scape.AdminChat")

    def do_activate(self):
        win = Gtk.ApplicationWindow(application=self, title="2009Scape Admin Chat")
        win.set_default_size(480, 360)
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        for m in ("top", "bottom", "start", "end"):
            getattr(box, f"set_margin_{m}")(10)
        self.log = Gtk.TextView(editable=False, cursor_visible=False, wrap_mode=Gtk.WrapMode.WORD)
        scroll = Gtk.ScrolledWindow(vexpand=True)
        scroll.set_child(self.log)
        row = Gtk.Box(spacing=8)
        self.entry = Gtk.Entry(hexpand=True, placeholder_text="Message to global chat (as Admin)")
        self.entry.connect("activate", self.send)
        btn = Gtk.Button(label="Send")
        btn.connect("clicked", self.send)
        row.append(self.entry)
        row.append(btn)
        box.append(scroll)
        box.append(row)
        win.set_child(box)
        win.present()
        self.entry.grab_focus()

    def note(self, text):
        buf = self.log.get_buffer()
        buf.insert(buf.get_end_iter(), f"{time.strftime('%H:%M:%S')}  {text}\n")

    def send(self, *_):
        text = " ".join(self.entry.get_text().split())
        if not text:
            return
        try:
            with open(SPOOL, "a", encoding="utf-8") as f:
                f.write(text + "\n")
            self.note(text)
            self.entry.set_text("")
        except OSError as e:
            self.note(f"ERROR: {e}")


if __name__ == "__main__":
    App().run()
