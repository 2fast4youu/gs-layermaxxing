"""Earned island building is atomic; free creative worlds never mutate it."""
from fastapi.testclient import TestClient
from test_v9_quests import auth, befriend, load_app, register


def state(c, a, mode="normal"):
    return c.get(f"/api/island/{a['user_id']}?mode={mode}", headers=auth(a)).json()


def action(c, a, st, action="upgrade", building="POST", mode="normal"):
    return c.post("/api/island/construction", headers=auth(a), json={
        "mode": mode, "action": action, "building": building, "expected_revision": st["construction"]["revision"]})


def earn(c, a, b, points=100):
    q = c.post("/api/quests", headers=auth(a), json={"peer_user_id": b['user_id'], "title": "Gemeinsam", "points": points}).json()
    assert c.patch(f"/api/quests/{q['id']}", headers=auth(b), json={"completed": True}).status_code == 200


def test_starter_has_no_house_or_completed_buildings(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c,"A")
        s = state(c,a)
        assert s['places'] == {} and s['plots'] == 0
        assert s['construction']['buildings'] == {} and s['construction']['land'] == 0
        assert c.put('/api/island/here',headers=auth(a),json={'plot':0}).status_code == 422
        assert c.put('/api/island/places',headers=auth(a),json={'places':{'0':{'kind':'home'}}}).status_code == 403
        assert state(c,a)['places'] == {}


def test_points_time_and_retries_are_real_gates(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a,b = register(c,'A'),register(c,'B'); befriend(c,a,b)
        assert action(c,a,state(c,a)).status_code == 403
        earn(c,a,b)
        before = state(c,a)
        assert action(c,a,before,action='activate',building=None).json()['construction']['revision'] == before['construction']['revision']
        assert action(c,a,before,action='activate',building=None).json()['construction']['revision'] == before['construction']['revision']
        assert action(c,a,before,action='expand',building=None).status_code == 403  # has points, not age
        built = action(c,a,before).json()
        assert built['construction']['buildings']['POST'] == 1
        assert built['construction']['available'] == 75
        assert action(c,a,before).status_code == 409  # stale retry cannot spend twice
        assert state(c,a)['construction']['spent'] == 25
        assert action(c,a,built).status_code == 403  # tier2 needs day2
        with m.db() as conn:
            conn.execute('UPDATE users SET created_at=? WHERE id=?',(m.now_ts()-3*86400,a['user_id']))
        upgraded=action(c,a,state(c,a)).json()
        assert upgraded['construction']['buildings']['POST']==2
        assert upgraded['construction']['spent']==85
        assert action(c,a,upgraded,action='expand',building=None).status_code==403  # insufficient remaining points


def test_creative_separate_persistent_and_friend_map_exact(tmp_path):
    m=load_app(tmp_path)
    with TestClient(m.app) as c:
        a,b,x=register(c,'A'),register(c,'B'),register(c,'X'); befriend(c,a,b)
        assert c.get(f"/api/island/{a['user_id']}?mode=creative",headers=auth(a)).status_code==403
        with m.db() as conn: conn.execute('UPDATE users SET creative_entitled=1 WHERE id=?',(a['user_id'],))
        creative=state(c,a,'creative')
        assert action(c,a,creative,action="activate",building=None,mode="creative").status_code==200
        for _ in range(3):
            r=action(c,a,creative,mode='creative'); assert r.status_code==200,r.text; creative=r.json()
        assert creative['construction']['spent']==0 and creative['construction']['buildings']['POST']==3
        expanded=action(c,a,creative,action='expand',building=None,mode='creative').json()
        assert expanded['construction']['land']==1
        seen=c.get(f"/api/island/{a['user_id']}",headers=auth(b)).json()
        thumbnail=c.get('/api/islands',headers=auth(b)).json()['friends'][0]['island']
        assert seen==thumbnail and 'here' not in seen
        assert seen['construction']['buildings']['POST']==3
        normal=state(c,a)
        assert normal['construction']['buildings']=={} and normal['construction']['land']==0
        assert action(c,a,normal,action='activate',building=None).status_code==200
        assert state(c,a,'creative')['construction']['buildings']['POST']==3
        # A late creative build after switching off changes its ledger, not the public mode.
        late=action(c,a,expanded,action='upgrade',building='HOUSE',mode='creative')
        assert late.status_code==200
        assert c.get(f"/api/island/{a['user_id']}",headers=auth(b)).json()['construction']['mode']=='normal'
        expanded=late.json()
        downgraded=action(c,a,expanded,action='downgrade',mode='creative').json()
        assert downgraded['construction']['buildings']['POST']==2
        assert action(c,a,normal,action='downgrade').status_code in (409,422)
        assert c.get(f"/api/island/{a['user_id']}",headers=auth(x)).status_code==404
        assert c.get(f"/api/island/{a['user_id']}?mode=normal",headers=auth(b)).status_code==403
        with m.db() as conn: conn.execute('UPDATE users SET creative_entitled=0 WHERE id=?',(a['user_id'],))
        assert action(c,a,downgraded,mode='creative').status_code==403
        assert c.get(f"/api/island/{a['user_id']}",headers=auth(b)).json()['construction']['mode']=='normal'


def test_land_expansion_creates_buildable_plots_and_persists_restart(tmp_path):
    m=load_app(tmp_path)
    with TestClient(m.app) as c:
        a,b=register(c,'A'),register(c,'B'); befriend(c,a,b); earn(c,a,b)
        with m.db() as conn: conn.execute('UPDATE users SET created_at=? WHERE id=?',(m.now_ts()-86400,a['user_id']))
        expanded=action(c,a,state(c,a),action='expand',building=None).json()
        assert expanded['plots']==3 and expanded['construction']['available']==60
        assert c.put('/api/island/places',headers=auth(a),json={'places':{'0':{'kind':'home','name':'Zuhause'}}}).status_code==200
        assert c.put('/api/island/here',headers=auth(a),json={'plot':0,'status':'privat'}).status_code==200
        assert 'here' not in c.get(f"/api/island/{a['user_id']}",headers=auth(b)).json()
        m.initialize_database()
        assert state(c,a)['construction']==expanded['construction']
        assert action(c,a,state(c,a),building='UNKNOWN').status_code==422
        assert state(c,a)['construction']['spent']==40
