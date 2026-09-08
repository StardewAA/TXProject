"""Local acceptance test. Requires an existing admin token JSON; creates labelled test fixtures."""
import json,os,secrets,subprocess,sys,time
from datetime import datetime,timedelta
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.error import HTTPError
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parents[2]
auth=json.loads((root/'.local-run/module6-auth.json').read_text(encoding='utf-8-sig'))
base=os.environ.get('MODULE6_API','http://127.0.0.1:48080/admin-api')
docker=os.environ.get('DOCKER_EXE',r'C:/Users/86158/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe')
checks=[];state={}
def api(path,data=None,token=None,ok=True):
 headers={'tenant-id':'1','Authorization':'Bearer '+(token or auth['accessToken']),'Content-Type':'application/json'}
 req=Request(base+path,data=json.dumps(data).encode() if data is not None else None,headers=headers)
 try:
  with urlopen(req,timeout=30) as r: body=json.load(r)
 except HTTPError as e: body=json.load(e)
 if ok: assert body.get('code')==0,(path,body)
 else: assert body.get('code')!=0,(path,'expected rejection')
 return body.get('data')
def live(path,data=None,token=None,ok=True):return api('/training/live-center/'+path,data,token,ok)
def passed(name):checks.append(name);print('PASS',name,flush=True)
def save():
 (root/'.local-run/module6-smoke-state.json').write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding='utf-8')
 (root/'.local-run/module6-smoke-results.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
def main():
 stamp=str(int(time.time()))[-7:];pw='M6_'+secrets.token_hex(5)
 for name in ['teacher','learner','peer']:
  username='m6_'+name+'_'+stamp
  uid=api('/system/user/create',{'username':username,'nickname':'模块六验收'+name,'password':pw,'remark':'模块六自动验收专用测试账号'})
  login=api('/system/auth/login',{'username':username,'password':pw})
  state[name]={'id':uid,'token':login['accessToken']};save()
 t=state['teacher']['token'];s=state['learner']['token'];other=state['peer']['token']
 live('profile/apply',{'userId':state['teacher']['id'],'name':'验收讲师','organization':'模块六测试单位','expertise':'安全培训','bio':'自动验收测试资料'})
 profile=next(p for p in live('profiles') if p['userId']==state['teacher']['id']);state['profileId']=profile['id'];save()
 live('profile/review',{'id':profile['id'],'status':'approved','comment':'本地验收通过'})
 assert live('context',token=t)['teacher'];passed('讲师申请、审核、授予讲师资格')
 live('profile/update',{'userId':state['teacher']['id'],'name':'验收讲师','organization':'模块六测试单位','expertise':'焊接与安全','bio':'资料更新无需复审'},t)
 now=datetime.now();fmt=lambda d:d.strftime('%Y-%m-%dT%H:%M:%S')
 room={'title':'模块六直播验收 '+stamp,'description':'自动推流、学习、作业与权限验收','coverUrl':'','planStartAt':fmt(now+timedelta(hours=1)),'planEndAt':fmt(now+timedelta(hours=2)),'visibility':'enterprise','streamUrl':'','creditHours':0.01}
 rid=live('room/save',room,t);state['roomId']=rid;save()
 live('room/start',{'id':rid},t,False);assert not any(r['id']==rid for r in live('rooms?scope=browse',token=s))
 live('room/review',{'id':rid,'status':'approved','comment':'测试预告审核'})
 live('room/book',{'id':rid},s);live('room/book',{'id':rid},s)
 r=next(r for r in live('rooms?scope=browse',token=s) if r['id']==rid);assert r['bookingCount']==1 and 'streamUrl' not in r
 live('room/save',{**room,'id':rid},other,False);live('stats?scope=platform',token=t,ok=False)
 passed('预告审核、预约幂等、学员不可越权修改、讲师统计隔离')
 push=live('stream/publish?roomId='+str(rid),token=t)
 live('room/start',{'id':rid},t);assert any(n['kind']=='start' and n['roomId']==rid for n in live('notices',token=s))
 logfile=(root/'.local-run/module6-ffmpeg.log').open('w')
 proc=subprocess.Popen([docker,'exec','txgy-live-srs','/usr/local/srs/objs/ffmpeg/bin/ffmpeg','-hide_banner','-loglevel','error','-re','-f','lavfi','-i','testsrc2=size=640x360:rate=25','-f','lavfi','-i','sine=frequency=440:sample_rate=44100','-t','90','-c:v','libx264','-preset','ultrafast','-tune','zerolatency','-g','50','-c:a','aac','-f','flv','rtmp://127.0.0.1/live/'+push['streamKey']],stdout=logfile,stderr=logfile)
 state['ffmpegPid']=proc.pid;save();time.sleep(8)
 assert proc.poll() is None,'推流失败，查看 module6-ffmpeg.log'
 media=live('playback?roomId='+str(rid),token=s);state['media']=media;save()
 req=Request('http://127.0.0.1:5174'+media['url'],headers={'Cookie':'training_live_media='+media['token']})
 with urlopen(req,timeout=15) as resp: assert b'#EXTM3U' in resp.read()
 try: urlopen('http://127.0.0.1:5174'+media['url']);raise AssertionError('匿名媒体访问未拦截')
 except HTTPError as e: assert e.code==403
 passed('真实 FFmpeg→RTMP→HLS 播放、开播提醒、匿名媒体拒绝')
 session=api('/training/portal/learner/session/start',{'mediaType':'live','mediaId':rid},s)
 time.sleep(5)
 hb=api('/training/portal/learner/session/heartbeat?sessionId='+str(session['sessionId']),{},s);assert hb['validSeconds']>=4
 api('/training/portal/learner/session/end?sessionId='+str(session['sessionId']),{},s)
 tracks=api('/training/portal/learner/track/list?pageNo=1&pageSize=10',token=s)
 assert any(r['mediaId']==rid and r['validSeconds']>0 and r['creditEarned']>0 for r in tracks['list'])
 passed('服务端心跳、有效学时与个人学习轨迹')
 live('config',{'danmakuEnabled':False,'micEnabled':False})
 api('/training/portal/learner/live/danmaku',{'roomId':rid,'content':'disabled'},s,False)
 live('config',{'danmakuEnabled':True,'micEnabled':False})
 api('/training/portal/learner/live/danmaku',{'roomId':rid,'content':'测试互动'},s)
 qid=live('quiz/save',{'roomId':rid,'question':'安全防护是否必要？','options':['是','否'],'correctAnswer':0},t)
 assert 'correctAnswer' not in live('quizzes?roomId='+str(rid),token=s)[0]
 live('quiz/answer',{'quizId':qid,'answer':0},s);live('quiz/answer',{'quizId':qid,'answer':1},s,False)
 aid=live('assignment/save',{'roomId':rid,'title':'安全培训作业','content':'说明个人防护要点','dueAt':fmt(now+timedelta(days=1))},t)
 live('assignment/submit',{'assignmentId':aid,'content':'佩戴安全帽，检查设备，遵循操作规范'},s)
 sub=live('submissions?assignmentId='+str(aid),token=t)[0]
 live('submission/grade',{'id':sub['id'],'score':95,'feedback':'要点完整'},t)
 own=live('assignments?roomId='+str(rid),token=s)[0];peer=live('assignments?roomId='+str(rid),token=other)[0]
 assert own['score']==95 and peer['score'] is None and peer['feedback'] is None
 live('submissions?assignmentId='+str(aid),token=other,ok=False)
 live('assignment/submit',{'assignmentId':aid,'content':'修改已批阅'},s,False)
 passed('弹幕全局开关、测验防重复、作业提交批阅及结果隐私')
 live('moderation',{'roomId':rid,'tenantId':1,'userId':state['learner']['id'],'action':'mute','reason':'验收禁言'},t)
 api('/training/portal/learner/live/danmaku',{'roomId':rid,'content':'muted'},s,False)
 live('moderation',{'roomId':rid,'tenantId':1,'userId':state['learner']['id'],'action':'kick','reason':'验收踢出'},t)
 live('playback?roomId='+str(rid),token=s,ok=False)
 live('moderation',{'roomId':rid,'tenantId':1,'userId':state['learner']['id'],'action':'restore','reason':'验收恢复'},t)
 passed('禁言、踢出、恢复与持久化场控日志')
 live('room/end',{'id':rid},t)
 for _ in range(15):
  r=next(r for r in live('rooms?scope=mine',token=t) if r['id']==rid)
  if r.get('replayTask',{}).get('status')=='ready':break
  time.sleep(2)
 assert r['replayTask']['status']=='ready','录制文件未自动归档'
 live('playback?roomId='+str(rid)+'&replay=true',token=s,ok=False)
 live('replay/review',{'id':rid,'status':'approved','comment':'自动录像审核通过'})
 replay=live('playback?roomId='+str(rid)+'&replay=true',token=s)
 req=Request('http://127.0.0.1:5174'+replay['url'],headers={'Cookie':'training_live_media='+replay['token'],'Range':'bytes=0-99'})
 with urlopen(req,timeout=15) as resp: assert b'ftyp' in resp.read(100)
 live('replay/toggle',{'id':rid,'enabled':False},t);live('playback?roomId='+str(rid)+'&replay=true',token=s,ok=False)
 live('replay/toggle',{'id':rid,'enabled':True},t)
 passed('结束推流、自动 MP4 归档、回放审核、停用与恢复')
 live('profile/toggle',{'id':profile['id'],'enabled':False});live('room/save',room,t,False)
 api('/training/course/create',{'title':'停用讲师测试课程'},t,False)
 live('profile/toggle',{'id':profile['id'],'enabled':True})
 assert any(r['id']==rid for r in live('stats?scope=mine',token=t))
 passed('讲师停用限制和本人运营数据')
 save();print('ALL CHECKS PASSED',flush=True)
try:main()
finally:save()
