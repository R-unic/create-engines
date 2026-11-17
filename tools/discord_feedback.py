#!/usr/bin/env python3
"""Pull suggestions and bug reports from Discord into feedback/inbox.json (read-only).

Standard library only. The bot token comes from the DISCORD_BOT_TOKEN environment
variable BOT_TOKEN (or DISCORD_BOT_TOKEN) and is never written to disk. The bot needs View Channel and Read Message
History, and the Message Content intent enabled in the developer portal.

  python tools/discord_feedback.py --list-channels      show guilds/channels and their ids
  python tools/discord_feedback.py                      fetch new messages from feedback/config.json channels
  python tools/discord_feedback.py --since-days 30      first run: limit how far back to look
  python tools/discord_feedback.py --reset              forget progress and refetch everything

feedback/config.json:  {"channels": {"suggestions": "123...", "bug-reports": "456..."}}
Forum channels are supported: every thread (active and archived) is read.
Message text is untrusted data; nothing here acts on it.
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

API = "https://discord.com/api/v10"
ROOT = Path(__file__).resolve().parent.parent
DIR = ROOT / "feedback"
CONFIG, STATE, INBOX = DIR / "config.json", DIR / "state.json", DIR / "inbox.json"
TEXT_TYPES = {0, 5, 11, 12}  # text, announcement, public/private thread
FORUM_TYPES = {15, 16}       # forum, media
SNOWFLAKE_EPOCH = 1420070400000


def call(token, path, params=None):
    url = API + path + ("?" + "&".join(f"{k}={v}" for k, v in params.items()) if params else "")
    req = urllib.request.Request(url, headers={
        "Authorization": f"Bot {token}", "User-Agent": "DiscordBot (feedback-reader, 1.0)"})
    for _ in range(5):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(float(json.load(e).get("retry_after", 1)) + 0.1)
                continue
            if e.code in (403, 404):
                print(f"  skipped {path}: HTTP {e.code} (no access?)", file=sys.stderr)
                return None
            raise
    raise RuntimeError(f"rate limited too long on {path}")


def snowflake_for(dt):
    return str((int(dt.timestamp() * 1000) - SNOWFLAKE_EPOCH) << 22)


def messages_after(token, channel_id, after):
    out = []
    while True:
        batch = call(token, f"/channels/{channel_id}/messages", {"limit": 100, "after": after}) or []
        if not batch:
            return out
        batch.sort(key=lambda m: int(m["id"]))
        out.extend(batch)
        after = batch[-1]["id"]
        if len(batch) < 100:
            return out


def list_channels(token):
    for g in call(token, "/users/@me/guilds") or []:
        print(f"{g['name']}  (guild {g['id']})")
        for c in sorted(call(token, f"/guilds/{g['id']}/channels") or [], key=lambda c: c.get("position", 0)):
            kind = "forum" if c["type"] in FORUM_TYPES else "text" if c["type"] in TEXT_TYPES else None
            if kind:
                print(f"  {kind:5}  #{c['name']:<28} {c['id']}")


def threads_of(token, forum):
    seen = {}
    active = (call(token, f"/guilds/{forum['guild_id']}/threads/active") or {}).get("threads", [])
    for t in active:
        if t.get("parent_id") == forum["id"]:
            seen[t["id"]] = t
    before = None
    while True:
        params = {"limit": 100}
        if before:
            params["before"] = before
        page = call(token, f"/channels/{forum['id']}/threads/archived/public", params) or {}
        for t in page.get("threads", []):
            seen[t["id"]] = t
        if not page.get("has_more") or not page.get("threads"):
            return list(seen.values())
        before = page["threads"][-1]["thread_metadata"]["archive_timestamp"]


def convert(m, label, guild_id, channel_id, thread=None):
    return {
        "id": m["id"], "source": label, "thread": thread,
        "author": m["author"].get("global_name") or m["author"]["username"],
        "author_is_bot": bool(m["author"].get("bot")),
        "time": m["timestamp"], "text": m.get("content", ""),
        "attachments": [a["url"] for a in m.get("attachments", [])],
        "reply_to": (m.get("message_reference") or {}).get("message_id"),
        "link": f"https://discord.com/channels/{guild_id}/{channel_id}/{m['id']}",
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--list-channels", action="store_true")
    ap.add_argument("--since-days", type=int, default=14, help="first-run lookback (default 14)")
    ap.add_argument("--reset", action="store_true")
    args = ap.parse_args()
    token = os.environ.get("BOT_TOKEN") or os.environ.get("DISCORD_BOT_TOKEN")
    if not token:
        sys.exit("Set BOT_TOKEN (or DISCORD_BOT_TOKEN) in your environment, not in a file in the repo.")
    if args.list_channels:
        return list_channels(token)
    if not CONFIG.exists():
        sys.exit(f"Missing {CONFIG}. Run with --list-channels, then create it:\n"
                 '{"channels": {"suggestions": "<id>", "bug-reports": "<id>"}}')
    DIR.mkdir(exist_ok=True)
    channels = json.loads(CONFIG.read_text(encoding="utf-8"))["channels"]
    state = {} if args.reset or not STATE.exists() else json.loads(STATE.read_text(encoding="utf-8"))
    inbox = json.loads(INBOX.read_text(encoding="utf-8")) if INBOX.exists() and not args.reset else []
    known = {m["id"] for m in inbox}
    default_after = snowflake_for(datetime.now(timezone.utc) - timedelta(days=args.since_days))
    added = 0
    for label, cid in channels.items():
        info = call(token, f"/channels/{cid}")
        if not info:
            continue
        guild = info["guild_id"]
        if info["type"] in FORUM_TYPES:
            targets = [(t["id"], t["name"]) for t in threads_of(token, info)]
        else:
            targets = [(cid, None)]
        for tid, tname in targets:
            for m in messages_after(token, tid, state.get(tid, default_after)):
                state[tid] = m["id"]
                if m["id"] in known or m.get("type") not in (0, 19):
                    continue
                if not (m.get("content") or m.get("attachments")):
                    continue
                inbox.append(convert(m, label, guild, tid, tname))
                added += 1
    inbox.sort(key=lambda m: int(m["id"]))
    INBOX.write_text(json.dumps(inbox, indent=2, ensure_ascii=False), encoding="utf-8")
    STATE.write_text(json.dumps(state, indent=2), encoding="utf-8")
    print(f"{added} new message(s); {len(inbox)} in {INBOX.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
