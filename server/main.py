"""
Beanshelf social server — accounts, follows, bean posts, feed, leaderboards,
cheers + comments, discover, shareable profiles.

FastAPI + SQLite, single file, no external services. Photos live on disk next
to the DB. Runs behind a Cloudflare tunnel at https://beans.beanshelf.ca.

Run:  python3 -m uvicorn main:app --host 0.0.0.0 --port 8787
"""

import base64
import hashlib
import html
import secrets
import sqlite3
import time
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import FileResponse, HTMLResponse
from pydantic import BaseModel, Field

ROOT = Path(__file__).parent
DB_PATH = ROOT / "beanshelf.db"
PHOTOS = ROOT / "photos"
PHOTOS.mkdir(exist_ok=True)
PUBLIC_BASE = "https://beans.beanshelf.ca"

app = FastAPI(title="Beanshelf Social")


def db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init() -> None:
    with db() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY,
                username TEXT UNIQUE NOT NULL,
                display TEXT NOT NULL,
                pw_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                token TEXT UNIQUE NOT NULL,
                created_at INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS follows (
                follower INTEGER NOT NULL REFERENCES users(id),
                followee INTEGER NOT NULL REFERENCES users(id),
                PRIMARY KEY (follower, followee)
            );
            CREATE TABLE IF NOT EXISTS posts (
                id TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL REFERENCES users(id),
                name TEXT NOT NULL,
                roaster TEXT NOT NULL DEFAULT '',
                origin TEXT NOT NULL DEFAULT '',
                variety TEXT NOT NULL DEFAULT '',
                process TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                rating REAL NOT NULL DEFAULT 0,
                photo_file TEXT,
                created_at INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS cheers (
                post_id TEXT NOT NULL REFERENCES posts(id),
                user_id INTEGER NOT NULL REFERENCES users(id),
                PRIMARY KEY (post_id, user_id)
            );
            CREATE TABLE IF NOT EXISTS comments (
                id TEXT PRIMARY KEY,
                post_id TEXT NOT NULL REFERENCES posts(id),
                user_id INTEGER NOT NULL REFERENCES users(id),
                text TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            """
        )


init()


def hash_pw(password: str, salt: str) -> str:
    return hashlib.sha256((salt + password).encode()).hexdigest()


def auth(authorization: str = Header(default="")) -> sqlite3.Row:
    token = authorization.removeprefix("Bearer ").strip()
    if not token:
        raise HTTPException(401, "missing token")
    with db() as conn:
        user = conn.execute("SELECT * FROM users WHERE token = ?", (token,)).fetchone()
    if user is None:
        raise HTTPException(401, "bad token")
    return user


class Credentials(BaseModel):
    username: str = Field(min_length=2, max_length=24, pattern=r"^[a-z0-9_]+$")
    password: str = Field(min_length=4, max_length=128)
    display: str = Field(default="", max_length=48)


class BeanPost(BaseModel):
    name: str = Field(min_length=1, max_length=80)
    roaster: str = Field(default="", max_length=80)
    origin: str = Field(default="", max_length=120)
    variety: str = Field(default="", max_length=80)
    process: str = Field(default="", max_length=40)
    notes: str = Field(default="", max_length=500)
    rating: float = Field(default=0, ge=0, le=5)
    photo_b64: str | None = Field(default=None, max_length=3_000_000)


class CommentBody(BaseModel):
    text: str = Field(min_length=1, max_length=400)


def user_json(u: sqlite3.Row) -> dict:
    return {"username": u["username"], "display": u["display"]}


POST_COLS = "posts.*, users.username AS username, users.display AS display"


def post_json(conn: sqlite3.Connection, p: sqlite3.Row, me_id: int) -> dict:
    cheers = conn.execute("SELECT COUNT(*) c FROM cheers WHERE post_id = ?", (p["id"],)).fetchone()["c"]
    i_cheered = conn.execute(
        "SELECT 1 FROM cheers WHERE post_id = ? AND user_id = ?", (p["id"], me_id)
    ).fetchone() is not None
    ccount = conn.execute("SELECT COUNT(*) c FROM comments WHERE post_id = ?", (p["id"],)).fetchone()["c"]
    return {
        "id": p["id"],
        "username": p["username"],
        "display": p["display"],
        "name": p["name"],
        "roaster": p["roaster"],
        "origin": p["origin"],
        "variety": p["variety"],
        "process": p["process"],
        "notes": p["notes"],
        "rating": p["rating"],
        "photoUrl": f"/photos/{p['photo_file']}" if p["photo_file"] else None,
        "createdAt": p["created_at"],
        "cheers": cheers,
        "iCheered": i_cheered,
        "commentCount": ccount,
    }


# ── auth ──────────────────────────────────────────────────────────────────

@app.post("/auth/register")
def register(body: Credentials):
    salt = secrets.token_hex(8)
    token = secrets.token_hex(32)
    try:
        with db() as conn:
            conn.execute(
                "INSERT INTO users (username, display, pw_hash, salt, token, created_at) VALUES (?,?,?,?,?,?)",
                (body.username, body.display or body.username, hash_pw(body.password, salt), salt, token, int(time.time())),
            )
    except sqlite3.IntegrityError:
        raise HTTPException(409, "username taken")
    return {"token": token, "username": body.username, "display": body.display or body.username}


@app.post("/auth/login")
def login(body: Credentials):
    with db() as conn:
        u = conn.execute("SELECT * FROM users WHERE username = ?", (body.username,)).fetchone()
    if u is None or hash_pw(body.password, u["salt"]) != u["pw_hash"]:
        raise HTTPException(401, "wrong username or password")
    return {"token": u["token"], "username": u["username"], "display": u["display"]}


# ── people: search, profile, follow, followers/following ───────────────────

def _following_ids(conn: sqlite3.Connection, me_id: int) -> set:
    return {r["followee"] for r in conn.execute("SELECT followee FROM follows WHERE follower = ?", (me_id,))}


@app.get("/users/search")
def search_users(q: str, me: sqlite3.Row = Depends(auth)):
    like = f"%{q.lower()}%"
    with db() as conn:
        rows = conn.execute(
            "SELECT * FROM users WHERE (username LIKE ? OR lower(display) LIKE ?) AND id != ? LIMIT 20",
            (like, like, me["id"]),
        ).fetchall()
        following = _following_ids(conn, me["id"])
    return [{**user_json(r), "following": r["id"] in following} for r in rows]


@app.get("/users/{username}/profile")
def profile(username: str, me: sqlite3.Row = Depends(auth)):
    with db() as conn:
        u = conn.execute("SELECT * FROM users WHERE username = ?", (username,)).fetchone()
        if u is None:
            raise HTTPException(404, "no such user")
        followers = conn.execute("SELECT COUNT(*) c FROM follows WHERE followee = ?", (u["id"],)).fetchone()["c"]
        following = conn.execute("SELECT COUNT(*) c FROM follows WHERE follower = ?", (u["id"],)).fetchone()["c"]
        beans = conn.execute("SELECT COUNT(*) c FROM posts WHERE user_id = ?", (u["id"],)).fetchone()["c"]
        i_follow = conn.execute(
            "SELECT 1 FROM follows WHERE follower = ? AND followee = ?", (me["id"], u["id"])
        ).fetchone() is not None
    return {
        **user_json(u),
        "followers": followers,
        "following": following,
        "beans": beans,
        "iFollow": i_follow,
        "isMe": u["id"] == me["id"],
        "profileUrl": f"{PUBLIC_BASE}/u/{u['username']}",
    }


@app.post("/follow/{username}")
def follow(username: str, me: sqlite3.Row = Depends(auth)):
    with db() as conn:
        target = conn.execute("SELECT id FROM users WHERE username = ?", (username,)).fetchone()
        if target is None:
            raise HTTPException(404, "no such user")
        if target["id"] == me["id"]:
            raise HTTPException(400, "can't follow yourself")
        conn.execute("INSERT OR IGNORE INTO follows (follower, followee) VALUES (?,?)", (me["id"], target["id"]))
    return {"ok": True}


@app.delete("/follow/{username}")
def unfollow(username: str, me: sqlite3.Row = Depends(auth)):
    with db() as conn:
        target = conn.execute("SELECT id FROM users WHERE username = ?", (username,)).fetchone()
        if target is None:
            raise HTTPException(404, "no such user")
        conn.execute("DELETE FROM follows WHERE follower = ? AND followee = ?", (me["id"], target["id"]))
    return {"ok": True}


def _people_list(username: str, me_id: int, follower_side: bool) -> list:
    # follower_side=True → who follows `username`; else → who `username` follows.
    with db() as conn:
        u = conn.execute("SELECT id FROM users WHERE username = ?", (username,)).fetchone()
        if u is None:
            raise HTTPException(404, "no such user")
        if follower_side:
            sql = ("SELECT users.* FROM follows JOIN users ON users.id = follows.follower"
                   " WHERE follows.followee = ? ORDER BY users.username")
        else:
            sql = ("SELECT users.* FROM follows JOIN users ON users.id = follows.followee"
                   " WHERE follows.follower = ? ORDER BY users.username")
        rows = conn.execute(sql, (u["id"],)).fetchall()
        my_following = _following_ids(conn, me_id)
    return [{**user_json(r), "following": r["id"] in my_following} for r in rows]


@app.get("/users/{username}/followers")
def followers(username: str, me: sqlite3.Row = Depends(auth)):
    return _people_list(username, me["id"], follower_side=True)


@app.get("/users/{username}/following")
def following_list(username: str, me: sqlite3.Row = Depends(auth)):
    return _people_list(username, me["id"], follower_side=False)


# ── posting, feed, discover, leaderboards ──────────────────────────────────

@app.post("/beans")
def post_bean(body: BeanPost, me: sqlite3.Row = Depends(auth)):
    post_id = secrets.token_hex(12)
    photo_file = None
    if body.photo_b64:
        try:
            raw = base64.b64decode(body.photo_b64)
        except Exception:
            raise HTTPException(400, "bad photo encoding")
        ext = "png" if raw[:8] == b"\x89PNG\r\n\x1a\n" else "jpg"
        photo_file = f"{post_id}.{ext}"
        (PHOTOS / photo_file).write_bytes(raw)
    with db() as conn:
        conn.execute(
            "INSERT INTO posts (id, user_id, name, roaster, origin, variety, process, notes, rating, photo_file, created_at)"
            " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            (post_id, me["id"], body.name, body.roaster, body.origin, body.variety,
             body.process, body.notes, body.rating, photo_file, int(time.time() * 1000)),
        )
    return {"id": post_id}


@app.get("/feed")
def feed(me: sqlite3.Row = Depends(auth), limit: int = 50):
    with db() as conn:
        rows = conn.execute(
            f"SELECT {POST_COLS} FROM posts JOIN users ON users.id = posts.user_id"
            " WHERE posts.user_id = ? OR posts.user_id IN (SELECT followee FROM follows WHERE follower = ?)"
            " ORDER BY posts.created_at DESC LIMIT ?",
            (me["id"], me["id"], min(limit, 200)),
        ).fetchall()
        return [post_json(conn, r, me["id"]) for r in rows]


@app.get("/discover")
def discover(me: sqlite3.Row = Depends(auth), limit: int = 50):
    with db() as conn:
        rows = conn.execute(
            f"SELECT {POST_COLS} FROM posts JOIN users ON users.id = posts.user_id"
            " ORDER BY posts.created_at DESC LIMIT ?",
            (min(limit, 200),),
        ).fetchall()
        return [post_json(conn, r, me["id"]) for r in rows]


@app.get("/users/{username}/beans")
def user_beans(username: str, me: sqlite3.Row = Depends(auth)):
    with db() as conn:
        rows = conn.execute(
            f"SELECT {POST_COLS} FROM posts JOIN users ON users.id = posts.user_id"
            " WHERE users.username = ? ORDER BY posts.created_at DESC LIMIT 100",
            (username,),
        ).fetchall()
        return [post_json(conn, r, me["id"]) for r in rows]


@app.get("/users/{username}/leaderboard")
def user_leaderboard(username: str, me: sqlite3.Row = Depends(auth)):
    with db() as conn:
        rows = conn.execute(
            f"SELECT {POST_COLS} FROM posts JOIN users ON users.id = posts.user_id"
            " WHERE users.username = ? AND posts.rating > 0 ORDER BY posts.rating DESC, posts.created_at DESC LIMIT 25",
            (username,),
        ).fetchall()
        return [post_json(conn, r, me["id"]) for r in rows]


# ── cheers + comments ──────────────────────────────────────────────────────

@app.post("/beans/{post_id}/cheers")
def cheer(post_id: str, me: sqlite3.Row = Depends(auth)):
    with db() as conn:
        if conn.execute("SELECT 1 FROM posts WHERE id = ?", (post_id,)).fetchone() is None:
            raise HTTPException(404, "no such post")
        conn.execute("INSERT OR IGNORE INTO cheers (post_id, user_id) VALUES (?,?)", (post_id, me["id"]))
        c = conn.execute("SELECT COUNT(*) c FROM cheers WHERE post_id = ?", (post_id,)).fetchone()["c"]
    return {"cheers": c, "iCheered": True}


@app.delete("/beans/{post_id}/cheers")
def uncheer(post_id: str, me: sqlite3.Row = Depends(auth)):
    with db() as conn:
        conn.execute("DELETE FROM cheers WHERE post_id = ? AND user_id = ?", (post_id, me["id"]))
        c = conn.execute("SELECT COUNT(*) c FROM cheers WHERE post_id = ?", (post_id,)).fetchone()["c"]
    return {"cheers": c, "iCheered": False}


@app.get("/beans/{post_id}/comments")
def get_comments(post_id: str, _: sqlite3.Row = Depends(auth)):
    with db() as conn:
        rows = conn.execute(
            "SELECT comments.*, users.username, users.display FROM comments"
            " JOIN users ON users.id = comments.user_id WHERE post_id = ? ORDER BY created_at ASC",
            (post_id,),
        ).fetchall()
    return [
        {"id": r["id"], "username": r["username"], "display": r["display"],
         "text": r["text"], "createdAt": r["created_at"]}
        for r in rows
    ]


@app.post("/beans/{post_id}/comments")
def add_comment(post_id: str, body: CommentBody, me: sqlite3.Row = Depends(auth)):
    cid = secrets.token_hex(10)
    with db() as conn:
        if conn.execute("SELECT 1 FROM posts WHERE id = ?", (post_id,)).fetchone() is None:
            raise HTTPException(404, "no such post")
        conn.execute(
            "INSERT INTO comments (id, post_id, user_id, text, created_at) VALUES (?,?,?,?,?)",
            (cid, post_id, me["id"], body.text.strip(), int(time.time() * 1000)),
        )
    return {"id": cid}


# ── photos + shareable profile landing page ────────────────────────────────

@app.get("/photos/{name}")
def photo(name: str):
    if not name.replace(".", "").replace("jpg", "").replace("png", "").isalnum() or "/" in name or ".." in name:
        raise HTTPException(400, "bad name")
    f = PHOTOS / name
    if not f.exists():
        raise HTTPException(404, "no photo")
    return FileResponse(f)


@app.get("/u/{username}", response_class=HTMLResponse)
def profile_page(username: str):
    """Human-facing invite landing. Opening beans.beanshelf.ca/u/<name> in a
    browser offers to open the app (custom scheme) or explains how to get it."""
    with db() as conn:
        u = conn.execute("SELECT * FROM users WHERE username = ?", (username,)).fetchone()
        if u is None:
            return HTMLResponse("<h1>No such user</h1>", status_code=404)
        beans = conn.execute("SELECT COUNT(*) c FROM posts WHERE user_id = ?", (u["id"],)).fetchone()["c"]
    name = html.escape(u["display"])
    uname = html.escape(u["username"])
    return HTMLResponse(f"""<!doctype html><html><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1">
<title>{name} on Beanshelf</title>
<style>
  body{{margin:0;background:#17100B;color:#F0E4D2;font-family:-apple-system,system-ui,sans-serif;
       display:flex;min-height:100vh;align-items:center;justify-content:center;text-align:center}}
  .card{{padding:40px 28px;max-width:340px}}
  .bean{{font-size:44px}} h1{{font-family:Georgia,serif;margin:.3em 0 .1em}}
  .at{{color:#D9A468;letter-spacing:.05em}} .sub{{color:#A38B72;margin:.6em 0 1.6em}}
  a.btn{{display:block;background:#D9A468;color:#17100B;text-decoration:none;font-weight:600;
        padding:14px;border-radius:12px;margin:10px 0}}
  a.ghost{{color:#D9A468;text-decoration:none;font-size:14px}}
</style></head><body><div class=card>
  <div class=bean>&#9749;</div>
  <h1>{name}</h1>
  <div class=at>@{uname}</div>
  <div class=sub>{beans} bean{'s' if beans != 1 else ''} on their Beanshelf</div>
  <a class=btn href="beanshelf://u/{uname}">Open in Beanshelf</a>
  <a class=ghost href="beanshelf://u/{uname}">Don't have the app? Ask {name} for it.</a>
</div></body></html>""")


@app.get("/ping")
def ping():
    return {"beanshelf": 1}
