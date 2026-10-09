"""HTTP/WSS integration tests run without paid Exotel or real AI model downloads."""
import base64
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect
import kallvo_gateway.server as gateway

class StubEngine:
    speaker_id = 2
    def synthesize(self,text,rate,voice):
        return b"\0\0" * (rate // 4)
    def transcribe(self,pcm,rate):
        return "hello"
    def reply(self,text,history):
        return "नमस्ते।"

class ServerIntegration(unittest.TestCase):
    def setUp(self):
        self.previous=gateway._engine
        gateway._engine=StubEngine()
        self.environment=patch.dict(os.environ,{
            "KALLVO_STREAM_BASIC_USER":"realtest-user-123456789",
            "KALLVO_STREAM_BASIC_PASSWORD":"test-secret-password-0123456789abcdef",
            "KALLVO_ADMIN_TOKEN":"test-admin-token-0123456789abcdefghijkl",
        })
        self.environment.start()
        self.client=TestClient(gateway.app)
    def tearDown(self):
        self.client.close()
        self.environment.stop()
        gateway._engine=self.previous
    def test_health_no_phone_number_or_secret(self):
        response=self.client.get("/health")
        self.assertEqual(response.status_code,200)
        self.assertEqual(response.json(),{"service":"kallvo-voicebot","ready":True})
    def test_voice_control_rejects_unauthorized_and_bad_body(self):
        self.assertEqual(self.client.post("/control/voice",json={"speaker_id":1}).status_code,401)
        auth={"Authorization":"Bearer "+os.environ["KALLVO_ADMIN_TOKEN"]}
        for value in (True,-1,10,"2"):
            self.assertEqual(self.client.post("/control/voice",
                headers=auth,json={"speaker_id":value}).status_code,422)
        self.assertEqual(self.client.post("/control/voice",
            headers=auth,content="not-json").status_code,422)
        success=self.client.post("/control/voice",headers=auth,json={"speaker_id":8})
        self.assertEqual(success.status_code,200)
        self.assertEqual(gateway._engine.speaker_id,8)
    def test_unauthorized_websocket_is_rejected_not_crashed(self):
        with self.assertRaises(WebSocketDisconnect) as result:
            with self.client.websocket_connect("/voicebot"):
                pass
        self.assertEqual(result.exception.code,1008)
        with self.assertRaises(WebSocketDisconnect):
            with self.client.websocket_connect("/voicebot",headers={"authorization":"garbage"}):
                pass
    def test_authenticated_stream_starts_and_stops(self):
        name=os.environ["KALLVO_STREAM_BASIC_USER"]
        secret=os.environ["KALLVO_STREAM_BASIC_PASSWORD"]
        value=base64.b64encode((name+":"+secret).encode()).decode()
        with self.client.websocket_connect("/voicebot",headers={
                "authorization":"Basic "+value}) as ws:
            ws.send_json({"event":"connected"})
            ws.send_json({"event":"start","stream_sid":"MZtest",
                           "start":{"media_format":{"encoding":"audio/x-raw","sample_rate":"8000","bit_rate":"16"}}})
            response=ws.receive_json()
            self.assertEqual(response["event"],"media")
            self.assertEqual(response["stream_sid"],"MZtest")
            self.assertEqual(set(response["media"]),{"payload"})
            ws.send_json({"event":"stop","stream_sid":"MZtest"})
    def test_no_default_credentials_allowed(self):
        with patch.dict(os.environ,{"KALLVO_STREAM_BASIC_USER":"REPLACE_WITH_USERNAME"}):
            with self.assertRaises(RuntimeError):
                gateway._secret("KALLVO_STREAM_BASIC_USER",16)

if __name__=="__main__":
    unittest.main()
