"""Earned island building is atomic; creative mode builds for free on the same real island."""
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
        # 7.9.7: building plots are gone (life places are special decoration); the legacy write is refused.
        assert c.put('/api/island/places',headers=auth(a),json={'places':{'0':{'kind':'home'}}}).status_code == 410
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


def test_creative_builds_free_on_the_real_island_and_keeps_everything(tmp_path):
    m=load_app(tmp_path)
    with TestClient(m.app) as c:
        a,b,x=register(c,'A'),register(c,'B'),register(c,'X'); befriend(c,a,b)
        assert c.get(f"/api/island/{a['user_id']}?mode=creative",headers=auth(a)).status_code==403
        earn(c,a,b)
        with m.db() as conn:
            conn.execute('UPDATE users SET created_at=? WHERE id=?',(m.now_ts()-86400,a['user_id']))
            # An existing life place from before 7.9.7 (the plot write itself is gone).
            conn.execute("INSERT INTO island_places(user_id,plot,kind,name) VALUES (?,0,'home','Altbau')",(a['user_id'],))
        earned=action(c,a,state(c,a),action='expand',building=None).json()  # 40 real points spent
        with m.db() as conn:
            conn.execute('UPDATE users SET creative_entitled=1 WHERE id=?',(a['user_id'],))
            # A stale separate creative world from 7.9.3 stays stored but is never shown again.
            conn.execute("INSERT INTO island_construction(user_id,mode,state) VALUES (?,'creative',?)",(a['user_id'],'{"buildings":{"HOUSE":3},"land":5,"spent":0,"revision":9}'))
        creative=state(c,a,'creative')
        # Same island: earned land, places and buildings, only the mode label differs.
        assert creative['places']=={'0':{'kind':'home','name':'Altbau'}} and creative['plots']==3
        assert creative['construction']['land']==1 and creative['construction']['buildings']=={}
        assert creative['construction']['mode']=='creative'
        assert [d['name'] for d in creative['construction']['decor'] if d['item']=='place_home']==['Altbau']
        assert action(c,a,creative,action="activate",building=None,mode="creative").status_code==200
        for _ in range(3):
            r=action(c,a,creative,mode='creative'); assert r.status_code==200,r.text; creative=r.json()
        assert creative['construction']['buildings']['POST']==3
        assert creative['construction']['spent']==earned['construction']['spent']==40  # free, no gate, no fake progress
        for _ in range(4): creative=action(c,a,creative,action='expand',building=None,mode='creative').json()
        assert creative['construction']['land']==5
        seen=c.get(f"/api/island/{a['user_id']}",headers=auth(b)).json()
        thumbnail=c.get('/api/islands',headers=auth(b)).json()['friends'][0]['island']
        assert seen==thumbnail and 'here' not in seen
        assert seen['construction']['buildings']['POST']==3 and seen['places']['0']['name']=='Altbau'
        # Switching creative off keeps everything exactly as built.
        normal=state(c,a)
        assert action(c,a,normal,action='activate',building=None).status_code==200
        normal=state(c,a)
        assert normal['construction']['buildings']['POST']==3 and normal['construction']['land']==5
        assert normal['places']=={'0':{'kind':'home','name':'Altbau'}}
        assert c.get(f"/api/island/{a['user_id']}",headers=auth(b)).json()['construction']['mode']=='normal'
        # Downgrade/shrink stay creative-only; the normal ledger was never replaced.
        assert action(c,a,normal,action='downgrade').status_code==422
        assert action(c,a,normal,action='shrink',building=None).status_code==422
        downgraded=action(c,a,normal,action='downgrade',mode='creative').json()
        assert downgraded['construction']['buildings']['POST']==2
        assert c.get(f"/api/island/{a['user_id']}",headers=auth(x)).status_code==404
        assert c.get(f"/api/island/{a['user_id']}?mode=normal",headers=auth(b)).status_code==403
        with m.db() as conn:
            stale=conn.execute("SELECT state FROM island_construction WHERE user_id=? AND mode='creative'",(a['user_id'],)).fetchone()[0]
            conn.execute('UPDATE users SET creative_entitled=0 WHERE id=?',(a['user_id'],))
        assert '"HOUSE": 3' in stale or '"HOUSE":3' in stale  # legacy data kept, not deleted
        assert action(c,a,downgraded,mode='creative').status_code==403
        assert c.get(f"/api/island/{a['user_id']}",headers=auth(b)).json()['construction']['mode']=='normal'


def test_demolish_in_both_modes_without_refund(tmp_path):
    m=load_app(tmp_path)
    with TestClient(m.app) as c:
        a,b=register(c,'A'),register(c,'B'); befriend(c,a,b); earn(c,a,b)
        built=c.post('/api/island/construction',headers=auth(a),json={'action':'upgrade','building':'POST','position':[10,11],'expected_revision':0}).json()
        assert built['construction']['spent']==25
        assert action(c,a,built,action='demolish',building='HOUSE').status_code==422  # nothing there
        gone=action(c,a,built,action='demolish').json()
        assert gone['construction']['buildings']=={} and 'POST' not in gone['construction'].get('positions',{})
        assert gone['construction']['spent']==25 and gone['construction']['available']==75  # no refund
        assert action(c,a,built,action='demolish').status_code==409  # stale retry
        with m.db() as conn: conn.execute('UPDATE users SET creative_entitled=1 WHERE id=?',(a['user_id'],))
        again=action(c,a,state(c,a,'creative'),mode='creative').json()
        assert action(c,a,again,action='demolish',mode='creative').json()['construction']['buildings']=={}
        assert state(c,a)['construction']['spent']==25


def test_decor_is_built_freely_and_old_slots_migrate_without_loss(tmp_path):
    m=load_app(tmp_path)
    with TestClient(m.app) as c:
        a,b=register(c,'A'),register(c,'B'); befriend(c,a,b); earn(c,a,b,points=50)
        # Old client puts decor on slots; nothing has been migrated yet.
        assert c.put('/api/island/decor',headers=auth(a),json={'slots':{'0':'flowers','2':'palm'}}).status_code==200
        s=state(c,a)
        assert s['decor']=={'0':'flowers','2':'palm'}
        objs={d['id']:d for d in s['construction']['decor']}
        assert objs[0]['position']==[8,9] and objs[0]['item']=='flowers' and objs[2]['position']==[7,11]
        rev=s['construction']['revision']
        def deco(st,act,**kw):
            body={'action':act,'expected_revision':st['construction']['revision'],**kw}
            return c.post('/api/island/construction',headers=auth(a),json=body)
        # Free placement: land, collision and unlock are validated.
        assert deco(s,'decor_place',item='bench',position=[0,0]).status_code==422
        assert deco(s,'decor_place',item='bench',position=[8,9]).status_code==422
        assert deco(s,'decor_place',item='ufo',position=[12,12]).status_code==422
        assert deco(s,'decor_place',item='windmill',position=[12,12]).status_code==403  # needs 200 points
        placed=deco(s,'decor_place',item='bench',position=[12,13]).json()
        new=[d for d in placed['construction']['decor'] if d['item']=='bench'][0]
        assert new['position']==[12,13] and new['id']>=100 and placed['construction']['revision']==rev+1
        assert deco(s,'decor_place',item='bench',position=[13,14]).status_code==409
        moved=deco(placed,'decor_move',decor_id=0,position=[11,7]).json()
        assert {d['id']:d['position'] for d in moved['construction']['decor']}[0]==[11,7]
        assert moved['decor']=={'2':'palm'}  # moved object left its old slot for old renders
        # Buildings cannot be built on decoration; decoration cannot be put on buildings.
        assert c.post('/api/island/construction',headers=auth(a),json={'action':'upgrade','building':'POST','position':[12,13],'expected_revision':moved['construction']['revision']}).status_code==422
        removed=deco(moved,'decor_remove',decor_id=new['id']).json()
        assert all(d['id']!=new['id'] for d in removed['construction']['decor'])
        assert deco(removed,'decor_remove',decor_id=12345).status_code==422
        # Restart keeps the free layout; legacy rows were never deleted.
        m.initialize_database()
        assert state(c,a)['construction']['decor']==removed['construction']['decor']
        with m.db() as conn:
            assert conn.execute('SELECT COUNT(*) FROM island_decor WHERE user_id=?',(a['user_id'],)).fetchone()[0]==2
        # Friends see the same free decoration.
        assert c.get(f"/api/island/{a['user_id']}",headers=auth(b)).json()['construction']['decor']==removed['construction']['decor']
        # Creative mode ignores unlocks but still validates land/collisions.
        with m.db() as conn: conn.execute('UPDATE users SET creative_entitled=1 WHERE id=?',(a['user_id'],))
        cr=state(c,a,'creative')
        assert c.post('/api/island/construction',headers=auth(a),json={'mode':'creative','action':'decor_place','item':'windmill','position':[0,0],'expected_revision':cr['construction']['revision']}).status_code==422
        ok=c.post('/api/island/construction',headers=auth(a),json={'mode':'creative','action':'decor_place','item':'windmill','position':[13,13],'expected_revision':cr['construction']['revision']})
        assert ok.status_code==200,ok.text


def test_land_expansion_creates_buildable_plots_and_persists_restart(tmp_path):
    m=load_app(tmp_path)
    with TestClient(m.app) as c:
        a,b=register(c,'A'),register(c,'B'); befriend(c,a,b); earn(c,a,b)
        with m.db() as conn:
            conn.execute('UPDATE users SET created_at=? WHERE id=?',(m.now_ts()-86400,a['user_id']))
            conn.execute("INSERT INTO island_places(user_id,plot,kind,name) VALUES (?,0,'home','Zuhause')",(a['user_id'],))
        expanded=action(c,a,state(c,a),action='expand',building=None).json()
        assert expanded['plots']==3 and expanded['construction']['available']==60
        assert c.put('/api/island/here',headers=auth(a),json={'plot':0,'status':'privat'}).status_code==200
        assert 'here' not in c.get(f"/api/island/{a['user_id']}",headers=auth(b)).json()
        m.initialize_database()
        migrated=state(c,a)['construction']
        assert action(c,a,state(c,a),building='HOUSE').status_code==422  # default placement cannot bypass the (migrated) place
        assert state(c,a)['construction']==migrated
        assert {k:migrated[k] for k in ('buildings','land','spent')}=={k:expanded['construction'][k] for k in ('buildings','land','spent')}
        assert action(c,a,state(c,a),building='UNKNOWN').status_code==422
        assert state(c,a)['construction']['spent']==40


def test_layout_moves_are_atomic_persistent_and_free(tmp_path):
    m=load_app(tmp_path)
    with TestClient(m.app) as c:
        a,b=register(c,'A'),register(c,'B'); befriend(c,a,b); earn(c,a,b)
        def placed(st, kind, building, position):
            return c.post('/api/island/construction',headers=auth(a),json={
                'mode':'normal','action':kind,'building':building,'position':position,
                'expected_revision':st['construction']['revision']})
        empty=state(c,a)
        assert placed(empty,'move','HOUSE',[10,11]).status_code==422
        built=placed(empty,'upgrade','POST',[10,11]).json()
        assert built['construction']['positions']['POST']==[10,11]
        assert built['construction']['spent']==25
        assert placed(built,'upgrade','HOUSE',[11,11]).status_code==422
        assert placed(built,'move','POST',[0,0]).status_code==422
        assert placed(built,'move','POST',[10]).status_code==422
        assert placed(built,'move','POST',[]).status_code==422
        assert state(c,a)['construction']==built['construction']
        moved=placed(built,'move','POST',[14,11]).json()
        assert moved['construction']['spent']==25
        assert moved['construction']['buildings']==built['construction']['buildings']
        assert placed(built,'move','POST',[10,11]).status_code==409
        assert c.get(f"/api/island/{a['user_id']}",headers=auth(b)).json()['construction']['positions']['POST']==[14,11]
        assert state(c,a)['construction']['positions']['POST']==[14,11]
        # Normal progress cannot be downgraded step by step (demolition is separate, without refund).
        assert placed(moved,'downgrade','POST',[14,11]).status_code==422
        assert state(c,a)['construction']['spent']==25


def test_layout_blocks_decor_and_places_in_both_directions(tmp_path):
    m=load_app(tmp_path)
    with TestClient(m.app) as c:
        a,b=register(c,'A'),register(c,'B'); befriend(c,a,b);earn(c,a,b);earn(c,a,b)
        with m.db() as conn: conn.execute('UPDATE users SET created_at=? WHERE id=?',(m.now_ts()-86400*3,a['user_id']))
        def build(st,p):
            return c.post('/api/island/construction',headers=auth(a),json={'action':'upgrade','building':'POST','position':p,'expected_revision':st['construction']['revision']})
        initial=state(c,a)
        built=build(initial,[8,9]).json()
        assert c.put('/api/island/decor',headers=auth(a),json={'slots':{'0':'flowers'}}).status_code==422
        assert action(c,a,built).status_code==200  # ordinary upgrade does not relocate
        current=state(c,a)
        moved=c.post('/api/island/construction',headers=auth(a),json={'action':'move','building':'POST','position':[10,6],'expected_revision':current['construction']['revision']}).json()
        expanded=action(c,a,moved,action='expand',building=None).json()
        assert c.put('/api/island/places',headers=auth(a),json={'places':{'0':{'kind':'home'}}}).status_code==410
        assert action(c,a,expanded,building='HOUSE').status_code==422  # default placement cannot bypass collision
        assert state(c,a)['construction']==expanded['construction']


def test_creative_shrink_never_saves_overlapping_layout_or_loses_buildings(tmp_path):
    m=load_app(tmp_path)
    with TestClient(m.app) as c:
        a=register(c,'A')
        with m.db() as conn: conn.execute('UPDATE users SET creative_entitled=1 WHERE id=?',(a['user_id'],))
        current=state(c,a,'creative')
        for _ in range(5): current=action(c,a,current,action='expand',building=None,mode='creative').json()
        for b,p in [('POST',[9,9]),('HOUSE',[11,9])]:
            r=c.post('/api/island/construction',headers=auth(a),json={'mode':'creative','action':'upgrade','building':b,'position':p,'expected_revision':current['construction']['revision']})
            assert r.status_code==200,r.text;current=r.json()
        # 7.9.11 compact footprints (version 4): 2 cells apart fit down to land 2 (one cell each), on land 1 the
        # big pieces have one ring each and would overlap. (7.9.10, version 3: 4 cells apart, 1 vs 2 rings.)
        assert m.FOOTPRINT_RINGS['big'][2]==0 and m.FOOTPRINT_RINGS['big'][1]==1
        for _ in range(3): current=action(c,a,current,action='shrink',building=None,mode='creative').json()
        assert current['construction']['land']==2
        assert action(c,a,current,action='shrink',building=None,mode='creative').status_code==422
        assert state(c,a,'creative')['construction']==current['construction']
        assert current['construction']['buildings']=={'POST':1,'HOUSE':1}
        # 7.9.9: stage 1 -> the free stage-0 piece -> gone; only then is there room to shrink.
        starter=action(c,a,current,action='downgrade',building='POST',mode='creative').json()
        assert starter['construction']['starters']==['POST']
        removed=action(c,a,starter,action='downgrade',building='POST',mode='creative').json()
        assert action(c,a,removed,action='shrink',building=None,mode='creative').status_code==200
