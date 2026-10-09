import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from kallvo_gateway.engine import quick_response,_local_ollama

class EngineRules(unittest.TestCase):
    def test_no_fake_appointments(self):
        self.assertIn("कन्फर्म नहीं",quick_response("कल अपॉइंटमेंट चाहिए",[]))
        self.assertIn("कन्फर्म नहीं",quick_response("कल 5 बजे",
                [("अपॉइंटमेंट","कृपया दिन और समय बताइए। मैं अपॉइंटमेंट कन्फर्म नहीं कर सकता।")]))
    def test_quick_safety(self):
        self.assertIn("ओटीपी",quick_response("ओटीपी शेयर करूं",[]))
        self.assertEqual(quick_response("detailed unrelated inquiry",[]),"")
    def test_no_remote_ollama(self):
        import os
        original=os.environ.get("KALLVO_OLLAMA_URL")
        try:
            os.environ["KALLVO_OLLAMA_URL"]="https://evil.example/api/chat"
            with self.assertRaises(RuntimeError):
                _local_ollama()
        finally:
            if original is None:
                os.environ.pop("KALLVO_OLLAMA_URL",None)
            else:
                os.environ["KALLVO_OLLAMA_URL"]=original

if __name__=="__main__":
    unittest.main()
