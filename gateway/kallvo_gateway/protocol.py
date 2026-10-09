"""Exotel bidirectional JSON and PCM16 little-endian transport."""
import base64
import math
RATES = (8000, 16000, 24000)

def check_rate(value):
    try:
        rate=int(value)
    except (TypeError, ValueError):
        raise ValueError("Invalid audio sample rate") from None
    if rate not in RATES:
        raise ValueError("Unsupported PCM16 rate")
    return rate

def decode_media(event):
    if event.get("event")!="media":
        raise ValueError("Not a media event")
    media_object=event.get("media")
    if not isinstance(media_object,dict):
        raise ValueError("Invalid media object")
    payload=media_object.get("payload")
    if not isinstance(payload,str) or not payload or len(payload)>150000:
        raise ValueError("Missing or oversize media payload")
    try:
        pcm=base64.b64decode(payload,validate=True)
    except Exception as e:
        raise ValueError("Bad base64") from e
    if len(pcm)>100000 or len(pcm)%2 or not pcm:
        raise ValueError("PCM16 alignment or size")
    return pcm

def rms(pcm):
    if len(pcm)%2:
        raise ValueError("Odd PCM length")
    audio=memoryview(pcm).cast("h")
    return math.sqrt(sum(int(x)*int(x) for x in audio)/len(audio)) if audio else 0

def frames(pcm,rate):
    rate=check_rate(rate)
    if not pcm or len(pcm)%2:
        raise ValueError("Bad outbound PCM")
    chunk=max(3200,rate*2//10)
    chunk+=(-chunk)%320
    for i in range(0,len(pcm),chunk):
        part=pcm[i:i+chunk]
        if len(part)<chunk:
            part+=b"\0"*(chunk-len(part))
        yield base64.b64encode(part).decode("ascii"),len(part)/(2*rate)

def media(sid,b64,chunk=None,timestamp=None):
    """Exotel outbound payload only. Chunk/timestamp are INBOUND metadata.

    They are intentionally not echoed or invented: the Voicebot protocol
    specifies only media.payload for server-to-caller frames.
    """
    if not sid or not isinstance(b64,str):
        raise ValueError("Missing stream id or media payload")
    return {"event":"media","stream_sid":sid,"media":{"payload":b64}}

def clear(sid):
    return {"event":"clear","stream_sid":sid}
