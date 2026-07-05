# Beanshelf server

FastAPI + SQLite social backend: accounts, follows, bean posts, feed, per-user
leaderboards. Single file (`main.py`), photos on disk in `photos/`, DB in
`beanshelf.db`.

## Run locally

```bash
python3 -m uvicorn main:app --host 0.0.0.0 --port 8787
```

Phones on the same Tailscale tailnet reach it at `http://100.75.23.96:8787`.

### Auto-start on login (launchd)

The server runs as a LaunchAgent so it survives reboots and restarts on crash:
`~/Library/LaunchAgents/dev.adamsjack.beanshelf-server.plist` (RunAtLoad +
KeepAlive, port 8787). Manage it with:

```bash
launchctl unload ~/Library/LaunchAgents/dev.adamsjack.beanshelf-server.plist   # stop
launchctl load   ~/Library/LaunchAgents/dev.adamsjack.beanshelf-server.plist   # start
launchctl list | grep beanshelf                                                # status
```

The **quick tunnel is NOT auto-started** on purpose: its URL changes every
restart, so KeepAlive would hand out a new address the app doesn't know. Once
you have a named tunnel (below), `cloudflared service install` gives it the same
always-on treatment with a stable URL.

## Public HTTPS (Cloudflare quick tunnel — no domain, no login)

```bash
./tunnel.sh quick     # starts server + prints a https://*.trycloudflare.com URL
./tunnel.sh url       # reprint the current URL
./tunnel.sh stop      # tear down
```

Anyone with the app can sign in against that URL from anywhere — no Tailscale.
Downside: the URL is random and **changes every restart**. Fine for testing;
for something permanent use a named tunnel below.

## Permanent HTTPS — LIVE at https://beans.beanshelf.ca (named tunnel)

Set up 2026-07-05. Tunnel `beanshelf`
(id 58512df8-c3c8-4d51-9363-6e1f0b24d8b2) routes `beans.beanshelf.ca` →
`http://127.0.0.1:8787`. Config at `~/.cloudflared/config.yml`. The app's
DEFAULT_SERVER points here.

Runs as a LaunchAgent (reboot-proof, KeepAlive), alongside the server one:
`~/Library/LaunchAgents/dev.adamsjack.beanshelf-tunnel.plist`. Manage:

```bash
launchctl unload ~/Library/LaunchAgents/dev.adamsjack.beanshelf-tunnel.plist  # stop
launchctl load   ~/Library/LaunchAgents/dev.adamsjack.beanshelf-tunnel.plist  # start
```

To reproduce from scratch on a new domain: `cloudflared tunnel login` (browser
auth), `cloudflared tunnel create beanshelf`, `cloudflared tunnel route dns
beanshelf beans.DOMAIN`, write the config.yml, load the LaunchAgent, and point
DEFAULT_SERVER in `ui/SocialScreen.kt` at the new hostname.

## Before opening to strangers

- Rate-limit `/auth/register` and `/auth/login`.
- Add password reset.
- Add moderation (delete user/post) — people will post photos.
- Consider Postgres + object storage (R2) once the single box feels full;
  or port to Cloudflare Workers + D1 + R2 for edge scale.
```
