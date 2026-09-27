import tempfile
import unittest
from app import create_app

class RecommendationIsolationTests(unittest.TestCase):
    def test_guest_never_receives_personalized_source(self):
        f=tempfile.NamedTemporaryFile(suffix='.db',delete=False); f.close()
        app=create_app(f.name,{'TESTING':True})
        result=app.test_client().get('/v1/recommendations').get_json()
        self.assertNotEqual(result['source'],'PERSONALIZED')
        self.assertIsNone(result['favoriteRevision'])

if __name__=='__main__': unittest.main()
