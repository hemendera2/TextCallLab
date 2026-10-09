import asyncio
import base64
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from kallvo_gateway.protocol import decode_media, frames, rms, check_rate, media
from kallvo_gateway.session import CallSession

class Socket:
    def __init__(self):
        self.events=[]
        self.close_codes=[]
    async def send_json(self,event):
        self.events.append(event)
    async def close(self,code=1000):
        self.close_codes.append(code)

class Engine:
    speaker_id=3
    def transcribe(self,pcm,rate):
        return "नमस्ते"
    def reply(self,text,history):
        return "कैसे मदद करूँ?"
    def synthesize(self,text,rate,sid):
        assert sid==3
        return b"\x01\x00"*int(rate*0.25)

class FaultyEngine(Engine):
    def synthesize(self,text,rate,sid):
        raise RuntimeError("TTS unavailable")

class Protocol(unittest.TestCase):
    def test_media_codec(self):
        data=b"\x00\x10"*1600
        obj={"event":"media","media":{"payload":base64.b64encode(data).decode()}}
        self.assertEqual(decode_media(obj),data)
        self.assertGreater(rms(data),3000)
        with self.assertRaises(ValueError):
            decode_media({"event":"media","media":{"payload":"invalid!"}})
        with self.assertRaises(ValueError):
            check_rate(44100)
        with self.assertRaises(ValueError):
            decode_media({"event":"media","media":[]})
        with self.assertRaises(ValueError):
            decode_media({"event":"media","media":{"payload":"AAA="}})
    def test_outbound_alignment(self):
        chunks=list(frames(b"\0"*32000,16000))
        self.assertEqual(len(chunks),10)
        for payload,seconds in chunks:
            self.assertEqual(len(base64.b64decode(payload)),3200)
            self.assertAlmostEqual(seconds,0.1)
        self.assertEqual(media("MZ1","abc",1,0),{"event":"media","stream_sid":"MZ1","media":{"payload":"abc"}})

class Session(unittest.IsolatedAsyncioTestCase):
    async def test_call_turn_and_cleanup(self):
        socket=Socket()
        session=CallSession(socket,Engine())
        await session.handle({"event":"start","stream_sid":"MZ1",
                              "start":{"media_format":{"sample_rate":8000}}})
        self.assertEqual(session.speaker,3)
        speak=base64.b64encode(b"\0\x10"*800).decode()
        mute=base64.b64encode(b"\0\0"*800).decode()
        for i in range(4):
            await session.handle({"event":"media","stream_sid":"MZ1",
                                  "media":{"payload":speak}})
        for i in range(8):
            await session.handle({"event":"media","stream_sid":"MZ1",
                                  "media":{"payload":mute}})
        await asyncio.sleep(.45)
        self.assertTrue(any(e["event"]=="media" for e in socket.events))
        await session.close()
        self.assertTrue(session.closed)
        self.assertFalse(session.history)
    async def test_reject_unsupported_audio_negotiation(self):
        session=CallSession(Socket(),Engine())
        await session.handle({"event":"start","stream_sid":"MZBAD",
                              "start":{"media_format":{"encoding":"audio/opus",
                                     "bit_rate":"16","sample_rate":"8000"}}})
        self.assertFalse(session.active)
        await session.handle({"event":"start","stream_sid":"MZOK",
                              "start":{"media_format":{"encoding":"audio/x-raw",
                                     "bit_rate":"16","sample_rate":"16000"}}})
        self.assertTrue(session.active)
        self.assertEqual(session.rate,16000)
        await session.close()
    async def test_single_barge_in_clear(self):
        sock=Socket()
        session=CallSession(sock,Engine())
        await session.handle({"event":"start","stream_sid":"MZ2","start":{}})
        noisy=base64.b64encode(b"\0\x10"*800).decode()
        for i in range(4):
            await session.handle({"event":"media","stream_sid":"MZ2","media":{"payload":noisy}})
        self.assertLessEqual(len([x for x in sock.events if x["event"]=="clear"]),1)
        await session.close()
    async def test_failed_voice_ends_gateway_stream(self):
        sock=Socket()
        call=CallSession(sock,FaultyEngine())
        await call.handle({"event":"start","stream_sid":"MZFAIL","start":{}})
        await asyncio.sleep(.1)
        self.assertTrue(call.closed)
        self.assertEqual(sock.close_codes,[1011])
    async def test_stream_guard(self):
        session=CallSession(Socket(),Engine())
        await session.handle({"event":"media","stream_sid":"wrong",
                              "media":{"payload":"bad!"}})
        self.assertEqual(len(session.pcm),0)
        await session.close()

if __name__=="__main__":
    unittest.main()
