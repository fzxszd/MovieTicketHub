import tempfile
import unittest
from app import create_app, connect
from favorites import process_operation
from recommendations import sync_maoyan_catalog


class RecommendationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.NamedTemporaryFile(suffix='.db', delete=False); self.tmp.close()
        self.app = create_app(self.tmp.name, {'TESTING': True}); self.client = self.app.test_client()
        r = self.client.post('/auth/register', json={'username':'alice','email':'alice@example.com','password':'password1','confirmPassword':'password1'})
        self.token = r.json['token']; self.headers={'Authorization':'Bearer '+self.token}
        with connect(self.tmp.name) as c:
            uid=c.execute('select id from user_accounts where email_normalized=?',('alice@example.com',)).fetchone()[0]
            process_operation(c,uid,{'clientOperationId':'operation-00000001','movieId':2,'targetState':True,'localSequence':1},'device-00000000000001')
            process_operation(c,uid,{'clientOperationId':'operation-00000002','movieId':7,'targetState':True,'localSequence':2},'device-00000000000001')

    def test_personalized_stable_and_excludes_favorites(self):
        a=self.client.get('/v1/recommendations?limit=10',headers=self.headers).json
        b=self.client.get('/v1/recommendations?limit=10',headers=self.headers).json
        self.assertEqual(a['source'],'PERSONALIZED'); self.assertEqual([x['movieId'] for x in a['items']], [x['movieId'] for x in b['items']])
        self.assertNotIn(2,[x['movieId'] for x in a['items']]); self.assertTrue(all(x['reasonText'] for x in a['items']))

    def test_collaborative_signal_surfaces_item_liked_by_similar_account(self):
        response = self.client.post('/auth/register', json={'username':'bob','email':'bob@example.com','password':'password1','confirmPassword':'password1'})
        bob_id = response.json['user']['id']
        with connect(self.tmp.name) as connection:
            process_operation(connection, bob_id, {'clientOperationId':'bob-operation-0001','movieId':2,'targetState':True,'localSequence':1}, 'bob-device-00000001')
            process_operation(connection, bob_id, {'clientOperationId':'bob-operation-0002','movieId':4,'targetState':True,'localSequence':2}, 'bob-device-00000001')
        result = self.client.get('/v1/recommendations?limit=10', headers=self.headers).json
        collaborative = [item for item in result['items'] if item['movieId'] == 4]
        self.assertTrue(collaborative)
        self.assertEqual('COLLABORATIVE', collaborative[0]['reasonType'])

    def test_guest_fallback_and_event_idempotence(self):
        response=self.client.get('/v1/recommendations').json; self.assertIn(response['source'],('FALLBACK_POPULAR','FALLBACK_NOW_PLAYING'))
        if response['items']:
            body={'eventId':'event-00000000000001','movieId':response['items'][0]['movieId'],'action':'CLICK'}
            self.assertEqual(self.client.post('/v1/recommendations/'+response['recommendationId']+'/events',json=body).status_code,202)
            self.assertEqual(self.client.post('/v1/recommendations/'+response['recommendationId']+'/events',json=body).status_code,202)

    def test_maoyan_catalog_preserves_id_title_and_poster_for_detail_consistency(self):
        payload = {'movieList': [{'id': 900001, 'nm': '猫眼测试电影', 'img': 'https://example.test/poster.jpg',
                                  'cat': '剧情,冒险', 'star': '演员甲,演员乙', 'wish': 88, 'sc': 9.3, 'rt': '2026-09-26'}]}
        with connect(self.tmp.name) as connection:
            self.assertTrue(sync_maoyan_catalog(connection, fetcher=lambda: payload))
            row = connection.execute('SELECT * FROM movie_catalog WHERE movie_id=900001').fetchone()
            self.assertEqual('猫眼测试电影', row['title'])
            self.assertEqual('https://example.test/poster.jpg', row['poster_url'])
            self.assertIn('演员甲', row['people_json'])


if __name__=='__main__': unittest.main()
