"""Bounded per-call turn state. No audio, transcript or number persistence."""
import asyncio
from .protocol import check_rate, decode_media, rms, frames, media, clear

class CallSession:
    def __init__(self, socket, engine, rate=8000):
        self.socket=socket
        self.engine=engine
        self.rate=check_rate(rate)
        self.sid=""
        self.active=False
        self.closed=False
        self.speaker=1
        self.history=[]
        self.pcm=bytearray()
        self.voiced_ms=0
        self.quiet_ms=0
        self.interrupted=False
        self.pending=None
        self.turn=None
        self.voice=None
        self.epoch=0
        self.chunk_index=0
        self.timestamp=0

    async def emit(self,value):
        if not self.closed:
            await self.socket.send_json(value)

    async def handle(self,event):
        if not isinstance(event,dict):
            return
        event_type=event.get("event")
        if event_type=="connected":
            return
        if event_type=="start":
            if self.active or not isinstance(event.get("stream_sid"),str):
                return
            sid=event["stream_sid"]
            if not sid or len(sid)>128:
                return
            start=event.get("start",{})
            self.rate=check_rate(start.get("media_format",{}).get("sample_rate",self.rate))
            self.sid=sid
            self.speaker=self.engine.speaker_id
            self.active=True
            self.voice=asyncio.create_task(self.say(
                "नमस्ते, मैं KALLVO एआई सेक्रेटरी हूँ। बताइए, कैसे मदद करूँ?"))
            return
        if event_type=="stop":
            await self.close()
            return
        if event_type=="clear":
            self.reset_audio()
            self.history.clear()
            return
        if event_type!="media" or not self.active or self.closed:
            return
        if event.get("stream_sid")!=self.sid:
            return
        pcm=decode_media(event)
        duration=len(pcm)*1000//(2*self.rate)
        if duration<=0 or duration>1000:
            return
        talking=rms(pcm)>=550
        if talking:
            self.voiced_ms+=duration
            self.quiet_ms=0
            if self.voiced_ms>=150 and not self.interrupted:
                old_voice=self.voice and not self.voice.done()
                old_answer=self.turn and not self.turn.done()
                if old_voice or old_answer:
                    self.interrupted=True
                    self.epoch+=1
                    if old_voice:
                        self.voice.cancel()
                        await self.emit(clear(self.sid))
            self.pcm.extend(pcm)
        elif self.voiced_ms:
            self.quiet_ms+=duration
            self.pcm.extend(pcm)
            if self.quiet_ms>=700:
                if self.voiced_ms>=300:
                    self.queue_turn(bytes(self.pcm))
                self.reset_audio()
        if len(self.pcm)>self.rate*2*12:
            self.queue_turn(bytes(self.pcm))
            self.reset_audio()

    def queue_turn(self,clip):
        if self.turn and not self.turn.done():
            self.pending=clip
        else:
            self.turn=asyncio.create_task(self.answer(clip))

    def reset_audio(self):
        self.pcm.clear()
        self.voiced_ms=0
        self.quiet_ms=0
        self.interrupted=False

    async def answer(self,clip):
        generation=self.epoch
        try:
            text=await asyncio.wait_for(
                asyncio.to_thread(self.engine.transcribe,clip,self.rate),12)
            if not text or self.closed or generation!=self.epoch:
                return
            response=await asyncio.wait_for(
                asyncio.to_thread(self.engine.reply,text,self.history),8)
            if not response or self.closed or generation!=self.epoch:
                return
            self.history.append((text,response))
            self.history=self.history[-6:]
            self.voice=asyncio.create_task(self.say(response))
            await self.voice
        except asyncio.CancelledError:
            return
        except Exception:
            if not self.closed and generation==self.epoch:
                self.voice=asyncio.create_task(self.say(
                    "माफ़ कीजिए, अभी सही जवाब नहीं दे पा रहा। कृपया दोबारा बताइए।"))
                await self.voice
        finally:
            if self.pending and not self.closed:
                item,self.pending=self.pending,None
                self.turn=asyncio.create_task(self.answer(item))

    async def say(self,text):
        generation=self.epoch
        try:
            pcm=await asyncio.wait_for(asyncio.to_thread(
                self.engine.synthesize,text,self.rate,self.speaker),15)
            if self.closed or generation!=self.epoch:
                return
            elapsed=self.timestamp
            for b64,seconds in frames(pcm,self.rate):
                if self.closed or generation!=self.epoch:
                    return
                self.chunk_index+=1
                await self.emit(media(self.sid,b64,self.chunk_index,elapsed))
                elapsed+=int(seconds*1000)
                await asyncio.sleep(seconds*0.85)
            self.timestamp=elapsed
            if not self.closed and generation==self.epoch:
                await self.emit({"event":"mark","stream_sid":self.sid,
                                 "mark":{"name":"kallvo-"+str(self.chunk_index)}})
        except asyncio.CancelledError:
            return
        except Exception:
            # Close provider WSS FIRST. Cancelling an answer task first may cancel
            # the TTS task it awaits, preventing the socket close and fallback.
            try:
                await self.socket.close(code=1011)
            except Exception:
                pass
            await self.close()

    async def close(self):
        self.closed=True
        self.epoch+=1
        self.reset_audio()
        self.pending=None
        for task in (self.turn,self.voice):
            if task and not task.done() and task is not asyncio.current_task():
                task.cancel()
        self.history.clear()
