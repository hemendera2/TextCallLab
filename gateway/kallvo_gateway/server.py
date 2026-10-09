"""Authenticated inbound Exotel Voicebot WebSocket, no call log / audio recording."""
import base64
import hmac
import os
from fastapi import FastAPI, Request, WebSocket, WebSocketDisconnect, HTTPException
from .engine import LiveEngine
from .session import CallSession

app=FastAPI(docs_url=None,redoc_url=None,openapi_url=None)
_engine=None

def _secret(name,min_len):
    value=os.getenv(name,"")
    if len(value)<min_len:
        raise RuntimeError("Missing or weak "+name)
    return value

@app.on_event("startup")
async def startup():
    global _engine
    _secret("KALLVO_STREAM_BASIC_USER",16)
    _secret("KALLVO_STREAM_BASIC_PASSWORD",24)
    _secret("KALLVO_ADMIN_TOKEN",24)
    _engine=LiveEngine()

def check_basic(authorization):
    try:
        if not authorization.startswith("Basic "):
            return False
        decoded=base64.b64decode(authorization[6:],validate=True).decode("utf-8")
        username,password=decoded.split(":",1)
        return (hmac.compare_digest(username,_secret("KALLVO_STREAM_BASIC_USER",16))
                and hmac.compare_digest(password,_secret("KALLVO_STREAM_BASIC_PASSWORD",24)))
    except (ValueError,UnicodeDecodeError,RuntimeError):
        return False

def check_admin(authorization):
    secret=os.getenv("KALLVO_ADMIN_TOKEN","")
    return len(secret)>=24 and hmac.compare_digest(authorization,"Bearer "+secret)

@app.get("/health")
async def health():
    return {"service":"kallvo-voicebot","ready":_engine is not None}

@app.post("/control/voice")
async def select_voice(request:Request):
    if not check_admin(request.headers.get("authorization","")):
        raise HTTPException(401,"Not authorized")
    payload=await request.json()
    if not isinstance(payload,dict) or set(payload)!={"speaker_id"} or type(payload["speaker_id"]) is not int or payload["speaker_id"] not in range(10):
        raise HTTPException(422,"speaker_id must be integer 0..9")
    if _engine is None:
        raise HTTPException(503,"Engine not ready")
    _engine.speaker_id=payload["speaker_id"]
    return {"ok":True,"speaker_id":_engine.speaker_id}

@app.websocket("/voicebot")
async def voicebot(socket:WebSocket):
    if _engine is None or not check_basic(socket.headers.get("authorization","")):
        await socket.close(code=1008)
        return
    await socket.accept()
    session=CallSession(socket,_engine)
    try:
        while not session.closed:
            message=await socket.receive_json()
            await session.handle(message)
    except (WebSocketDisconnect,ValueError,RuntimeError):
        pass
    finally:
        await session.close()
