import importlib
from datetime import timedelta
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select
import app as m
import integration as integration

@pytest.fixture
def env(tmp_path, monkeypatch):
    m.tenant_engine.cache_clear()
    monkeypatch.setattr(m,'DATA',tmp_path)
    monkeypatch.setattr(integration,'DATA',tmp_path)
    actors={'value':m.Actor(id=1,name='管理员',role='admin',tenant_id=1,super_admin=True)}
    m.app.dependency_overrides[m.current_user]=lambda:actors['value']
    monkeypatch.setattr(integration,'platform_get',lambda *args:[{'id':1,'nickname':'管理员'},{'id':101,'nickname':'张学员'},{'id':102,'nickname':'李学员'}])
    with TestClient(m.app) as client:
        yield client,actors
    m.app.dependency_overrides.clear()
    for p in tmp_path.glob('*.db'):
        pass
    m.tenant_engine.cache_clear()

def value(response):
    assert response.status_code==200,response.text
    return response.json()['data']

def question(c,qtype='single'):
    body={'q_type':qtype,'stem':'测试题：请选择正确答案','options':[{'key':'A','text':'选项一'},{'key':'B','text':'选项二'}] if qtype=='single' else None,'answer':['A'] if qtype=='single' else {'points':'先断电再救援'},'analysis':'正确答案解析','tags':{'course_names':['安全培训'],'work_types':['防腐工']}}
    q=value(c.post('/api/questions',json=body))
    assert q['status']=='draft'
    value(c.post(f"/api/questions/{q['id']}/submit"))
    value(c.post(f"/api/questions/{q['id']}/approve"))
    return q

def schedule(c,qids,pass_score=60,retries=0,cert_type='graduate'):
    p=value(c.post('/api/papers',json={'name':'安全培训考核','course_name':'安全培训','pass_score':pass_score,'duration_min':30,'retry_limit':retries,'cert_type':cert_type}))
    value(c.post(f"/api/papers/{p['id']}/questions",json=[{'question_id':qid,'score':100/len(qids)} for qid in qids]))
    value(c.post(f"/api/papers/{p['id']}/publish"))
    s=value(c.post('/api/admin/exam-schedules',json={'name':'培训考试第一场','paper_id':p['id'],'start_at':(m.now()-timedelta(minutes=1)).isoformat()+'Z','end_at':(m.now()+timedelta(hours=1)).isoformat()+'Z','duration_min':20,'user_ids':[101]}))
    value(c.post(f"/api/admin/exam-schedules/{s['id']}/publish"))
    return p,s

def learner(actors,uid=101,tenant=1):actors['value']=m.Actor(id=uid,name='张学员',role='learner',tenant_id=tenant)
def administrator(actors):actors['value']=m.Actor(id=1,name='管理员',role='admin',tenant_id=1,super_admin=True)
def start(c):
    task=value(c.get('/api/my/exams'))[0]
    return value(c.post(f"/api/my/exams/{task['id']}/start"))

def test_full_objective_workflow_and_cert_ownership(env):
    c,a=env;q=question(c);p,s=schedule(c,[q['id']]);learner(a)
    attempt=start(c);assert 'answer' not in attempt['questions'][0] and 'analysis' not in attempt['questions'][0]
    aid=attempt['attempt_id'];value(c.post(f'/api/my/exams/attempts/{aid}/answers',json={'question_id':q['id'],'user_answer':['A']}))
    resumed=start(c);assert resumed['answers'][str(q['id'])]==['A']
    value(c.post(f'/api/my/exams/attempts/{aid}/submit'));value(c.post(f'/api/my/exams/attempts/{aid}/submit'))
    result=value(c.get(f'/api/my/exams/attempts/{aid}/result'));assert result['is_pass'] and result['total_score']==100
    certs=value(c.get('/api/my/certs'));assert len(certs)==1
    pdf=c.get(f"/api/my/certs/{certs[0]['id']}/download");assert pdf.content.startswith(b'%PDF') and b'STSong-Light' in pdf.content
    learner(a,102);assert c.get(f"/api/my/certs/{certs[0]['id']}").status_code==404
    assert c.get('/api/questions').status_code==403

def test_subjective_grading_then_wrong_book(env):
    c,a=env;q1=question(c);q2=question(c,'essay');schedule(c,[q1['id'],q2['id']],pass_score=60);learner(a)
    at=start(c);aid=at['attempt_id']
    for q,v in [(q1,['A']),(q2,'处理顺序')]:value(c.post(f'/api/my/exams/attempts/{aid}/answers',json={'question_id':q['id'],'user_answer':v}))
    result=value(c.post(f'/api/my/exams/attempts/{aid}/submit'));assert result['status']=='submitted';assert not value(c.get('/api/my/certs'))
    administrator(a)
    assert c.post(f'/api/admin/grading/{aid}/grade',json={'answers':[{'question_id':q2['id'],'score':51}]}).status_code==422
    value(c.post(f'/api/admin/grading/{aid}/grade',json={'answers':[{'question_id':q2['id'],'score':20,'comment':'要点不全'}]}))
    learner(a);assert len(value(c.get('/api/my/certs')))==1
    wrong=value(c.get('/api/my/wrong-questions?course=安全'));assert len(wrong)==1 and wrong[0]['course_name']=='安全培训'
    assert value(c.get('/api/my/wrong-questions?course=不存在'))==[]

def test_tenant_and_permission_isolation(env):
    c,a=env;question(c)
    a['value']=m.Actor(id=1,name='企业二管理员',role='admin',tenant_id=2)
    assert value(c.get('/api/questions'))==[]
    assert c.post('/api/questions/1/approve').status_code==409
    assert c.get('/api/admin/cert-issuers').status_code==403
    a['value']=m.Actor(id=2,name='查看者',role='viewer',tenant_id=1)
    assert c.post('/api/questions',json={'q_type':'judge','stem':'判断试题','answer':[True]}).status_code==403

def test_expired_attempt_rejects_late_answer(env):
    c,a=env;q=question(c);schedule(c,[q['id']]);learner(a);at=start(c);aid=at['attempt_id']
    with m.Session(m.tenant_engine(1)) as db:
        db.get(m.ExamAttempt,aid).deadline_at=m.now()-timedelta(seconds=1);db.commit()
    assert c.post(f'/api/my/exams/attempts/{aid}/answers',json={'question_id':q['id'],'user_answer':['A']}).status_code==409
    r=value(c.get(f'/api/my/exams/attempts/{aid}/result'));assert r['status']=='graded' and r['total_score']==0

def test_random_pool_validation_and_audit(env):
    c,a=env;q=question(c)
    rule={'name':'随机卷','pass_score':10,'sections':[{'q_type':'single','count':2,'score_each':10}]}
    assert c.post('/api/papers/auto-generate',json=rule).status_code==422
    rule['sections'][0]['count']=1;assert value(c.post('/api/papers/auto-generate',json=rule))['question_count']==1
    rule['sections']*=2;assert c.post('/api/papers/auto-generate',json=rule).status_code==422
    value(c.put(f"/api/questions/{q['id']}",json={'q_type':'judge','stem':'编辑后的判断题','answer':[True]}))
    assert value(c.get('/api/questions'))[0]['status']=='draft'
    p=value(c.post('/api/papers',json={'name':'手动卷','pass_score':10}))
    assert c.post(f"/api/papers/{p['id']}/questions",json=[{'question_id':q['id'],'score':100}]).status_code==422

def test_invalid_options_future_schedule_and_roster(env):
    c,a=env
    assert c.post('/api/questions',json={'q_type':'single','stem':'非法选项题目','options':[{'key':'A','text':'内容'},{'key':'B','text':'内容'}],'answer':['C']}).status_code==422
    q=question(c);p,s=schedule(c,[q['id']])
    assert c.post('/api/admin/exam-schedules',json={'name':'无效人员','paper_id':p['id'],'start_at':m.now().isoformat(),'end_at':(m.now()+timedelta(hours=1)).isoformat(),'user_ids':[999]}).status_code==422
    with m.Session(m.tenant_engine(1)) as db:db.get(m.ExamSchedule,s['id']).start_at=m.now()+timedelta(hours=1);db.commit()
    learner(a);task=value(c.get('/api/my/exams'))[0];assert c.post(f"/api/my/exams/{task['id']}/start").status_code==409

def test_real_auth_does_not_accept_demo_headers(monkeypatch):
    m.app.dependency_overrides.clear()
    with TestClient(m.app) as c:
        assert c.get('/api/session',headers={'X-User-Id':'1','X-Role':'admin','tenant-id':'1'}).status_code==401
    monkeypatch.setattr(m,'platform_get',lambda *args:{'user':{'id':1,'tenantId':2},'roles':['super_admin'],'permissions':[]})
    with pytest.raises(m.HTTPException) as e:m.current_user('Bearer test-session',1)
    assert e.value.status_code==403

def test_background_deadline_submits_without_browser(env):
    import time
    c,a=env;q=question(c);schedule(c,[q['id']]);learner(a);at=start(c)
    with m.Session(m.tenant_engine(1)) as db:
        db.get(m.ExamAttempt,at['attempt_id']).deadline_at=m.now()-timedelta(seconds=1);db.commit()
    time.sleep(6)
    with m.Session(m.tenant_engine(1)) as db:
        answer=db.get(m.ExamAttempt,at['attempt_id']);assert answer.status=='graded' and answer.submit_mode=='auto'

def test_joint_certificate_never_fakes_external_success(env):
    c,a=env
    with m.Session(m.tenant_engine(1)) as db:
        db.add(m.CertIssuer(name='隔离验收机构',api_url='https://example.org/issue',api_key='test-only',enabled=True,issue_on_pass=True));db.commit()
    q=question(c);schedule(c,[q['id']],cert_type='joint');learner(a);at=start(c)
    value(c.post(f"/api/my/exams/attempts/{at['attempt_id']}/answers",json={'question_id':q['id'],'user_answer':['A']}))
    value(c.post(f"/api/my/exams/attempts/{at['attempt_id']}/submit"))
    administrator(a);logs=value(c.get('/api/admin/cert-sync-logs'));assert len(logs)==1 and logs[0]['status']=='pending' and logs[0]['ext_cert_no'] is None
