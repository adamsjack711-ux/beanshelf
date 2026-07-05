"""
Beanshelf social server — accounts, follows, bean posts, feed, leaderboards.

FastAPI + SQLite, single file, no external services. Photos live on disk next
to the DB. Designed to run anywhere; today it runs on Jack's Mac and phones
reach it over Tailscale/LAN.

Run:  python3 -m uvicorn main:app --host 0.0.0.0 --port 8787
"""

import base64
import hashlib
import secrets
import sqlite3
import time
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

ROOT = Path(__file__).parent
DB_PATH = ROOT / "beanshelf.db"
PHOTOS = ROOT / "photos"
PHOTOS.mkdir(exist_ok=True)

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
    photo_b64: str | None = Field(default=None, max_length=3_000_000)  # ~2MB decoded


def user_json(u: sqlite3.Row) -> dict:
    return {"username": u["username"], "display": u["display"]}


def post_json(p: sqlite3.Row) -> dict:
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
    }


POST_COLS = "posts.*, users.username AS username, users.display AS display"


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


@app.get("/users/search")
def search_users(q: str, me: sqlite3.Row = Depends(auth)):
    like = f"%{q.lower()}%"
    with db() as conn:
        rows = conn.execute(
            "SELECT * FROM users WHERE (username LIKE ? OR lower(display) LIKE ?) AND id != ? LIMIT 20",
            (like, like, me["id"]),
        ).fetchall()
        following = {
            r["followee"] for r in conn.execute("SELECT followee FROM follows WHERE follower = ?", (me["id"],))
        }
    return [{**user_json(r), "following": r["id"] in following} for r in rows]


@app.post("/follow/{username}")
def follow(username: str, me: sqlite3.Row = Depends(auth)):
    with db() as conn:
        target = conn.execute("SELECT id FROM users WHERE username = ?", (username,)).fetchone()
        if target is None:
            raise HTTPException(404, "no such user")
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
    return [post_json(r) for r in rows]


@app.get("/users/{username}/beans")
def user_beans(username: str, _: sqlite3.Row = Depends(auth)):
    with db() as conn:
        rows = conn.execute(
            f"SELECT {POST_COLS} FROM posts JOIN users ON users.id = posts.user_id"
            " WHERE users.username = ? ORDER BY posts.created_at DESC LIMIT 100",
            (username,),
        ).fetchall()
    return [post_json(r) for r in rows]


@app.get("/users/{username}/leaderboard")
def user_leaderboard(username: str, _: sqlite3.Row = Depends(auth)):
    with db() as conn:
        rows = conn.execute(
            f"SELECT {POST_COLS} FROM posts JOIN users ON users.id = posts.user_id"
            " WHERE users.username = ? AND posts.rating > 0 ORDER BY posts.rating DESC, posts.created_at DESC LIMIT 25",
            (username,),
        ).fetchall()
    return [post_json(r) for r in rows]


@app.get("/photos/{name}")
def photo(name: str):
    # ids are unguessable hex; extension whitelist keeps this from serving arbitrary files
    if not name.replace(".", "").replace("jpg", "").replace("png", "").isalnum() or "/" in name or ".." in name:
        raise HTTPException(400, "bad name")
    f = PHOTOS / name
    if not f.exists():
        raise HTTPException(404, "no photo")
    return FileResponse(f)


@app.get("/ping")
def ping():
    return {"beanshelf": 1}
