import { editorActions } from './editors.js';
export const state={session:null,token:'',tenant:0,mode:new URLSearchParams(location.search).get('mode')||'admin',embedded:new URLSearchParams(location.search).get('nav')==='0',data:{},active:'overview',attempt:null,pending:new Set(),drafts:{},errors:{}};
export const $=(q,root=document)=>root.querySelector(q);
export const esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const types={single:'单选题',multi:'多选题',judge:'判断题',essay:'实操问答',case:'案例分析',blank:'填空题',short:'简答题'};
export const exams={course_exam:'结课考试',mock:'模拟考试',cert:'取证考试',qualification:'职业资格考试',enterprise:'企业定制'};
export const certTypes={graduate:'结业证书',competence:'岗位能力认证',joint:'联合认证'};
export const statusNames={draft:'草稿',pending:'待审核',approved:'已通过',rejected:'已驳回',disabled:'已停用',published:'已发布',scheduled:'已排考',not_open:'未开始',waiting:'可参加',doing:'进行中',in_progress:'答题中',submitted:'待阅卷',graded:'已出成绩',finished:'已完成',missed:'已结束',success:'同步成功',failed:'同步失败',valid:'有效'};
export const badge=s=>`<span class="badge ${esc(s)}">${esc(statusNames[s]||s)}</span>`;
export const date=s=>s?new Date(/[zZ]|[+-]\d\d:\d\d$/.test(s)?s:s+'Z').toLocaleString('zh-CN',{hour12:false}):'—';
export const button=(label,action,id='',cls='soft small')=>`<button type="button" class="${cls}" data-action="${action}" data-id="${esc(id)}">${label}</button>`;
export const table=(heads,rows)=>rows.length?`<div class="tablewrap"><table><thead><tr>${heads.map(x=>`<th>${x}</th>`).join('')}</tr></thead><tbody>${rows.join('')}</tbody></table></div>`:'<div class="empty"><strong>暂无记录</strong>完成相关操作后，数据会显示在这里。</div>';
export const courseExamsSection=(rows,papers)=>{const name=id=>{const p=(papers||[]).find(x=>x.id===Number(id));return p?p.name:'#'+id};return `<h3 style="margin-top:22px">课程结课考试（来自课程管理）</h3><div class="note">课程管理（模块二）为课程配置的结课考试与考试时间会同步展示在这里。</div>${table(['课程名称','结课考试试卷','考试时间'],(rows||[]).map(c=>`<tr><td>${esc(c.course_name||'—')}</td><td>${c.exam_id?esc(name(c.exam_id)):'—'}</td><td>${c.exam_start_time?esc(c.exam_start_time):'—'} ~ ${c.exam_end_time?esc(c.exam_end_time):'—'}</td></tr>`))}`};
export function toast(msg){$('#toast').textContent=msg;$('#toast').classList.add('show');clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('#toast').classList.remove('show'),4500)}
export async function api(path,opt={}){
 const r=await fetch(`/training-exam/api${path}`,{...opt,...(!opt.method&&state.controller?{signal:state.controller.signal}:{}),headers:{'Content-Type':'application/json','Authorization':state.token,'tenant-id':String(state.tenant),...opt.headers}});
 const result=await r.json().catch(()=>({detail:'服务返回异常，请稍后重试'}));
 if(!r.ok||result.code&&result.code!==0){if(r.status===401)parent.postMessage({type:'training-exam:expired'},location.origin);const d=result.detail;throw new Error(typeof d==='string'?d:Array.isArray(d)?d.map(x=>x.msg).join('；'):d?.message||result.message||'操作失败')}
 return result.data;
}
export const post=(path,body)=>api(path,{method:'POST',...(body===undefined?{}:{body:JSON.stringify(body)})});
export function modal(title,content,saveLabel,onSave){
 const d=document.createElement('dialog');d.innerHTML=`<form class="modal"><div class="modalhead"><h2>${esc(title)}</h2><button type="button" class="ghost" data-close aria-label="关闭">×</button></div>${content}<div class="actions"><button type="button" class="ghost" data-close>关闭</button>${onSave?`<button type="submit">${esc(saveLabel||'保存')}</button>`:''}</div></form>`;document.body.append(d);
 d.querySelectorAll('[data-close]').forEach(x=>x.onclick=()=>d.close());d.onclose=()=>d.remove();d.showModal();d.querySelector('form').onsubmit=async e=>{e.preventDefault();if(!d.querySelector('form').reportValidity())return;const b=d.querySelector('[type=submit]');b.disabled=true;try{await onSave(d);d.close()}catch(e){toast(e.message)}finally{b.disabled=false}};return d;
}
export const author=()=>['admin','teacher'].includes(state.session?.role);
export const reviewer=()=>['admin','reviewer'].includes(state.session?.role);
export const admin=()=>state.session?.role==='admin';
const navs={overview:'总览',questions:'题库查询',papers:'试卷管理',schedules:'考试安排',grading:'阅卷与成绩',certificates:'证书管理',issuers:'联合认证',myexams:'我的考试',wrong:'错题复盘',mycerts:'我的证书'};
function available(){if(state.mode==='learner'||state.session?.role==='learner')return ['myexams','wrong','mycerts'];const r=state.session?.role;return ['overview',...(r!=='viewer'?['questions']:[]),'papers',...(admin()?['schedules']:[]),'grading','certificates',...(state.session?.super_admin?['issuers']:[]),'myexams','wrong','mycerts']}
export async function load(page=state.active){
 if(state.attempt&&page!=='taking'){toast('请先交卷，再离开答题页面');return}
 const version=(state.viewVersion||0)+1;state.viewVersion=version;state.controller?.abort();state.controller=new AbortController();
 state.active=available().includes(page)?page:available()[0];if(state.embedded){parent.postMessage({type:'training-exam:navigate',page:state.active},location.origin)}else{$('#nav').innerHTML=available().map(k=>`<button class="${k===state.active?'active':''}" data-action="navigate" data-id="${k}">${navs[k]}</button>`).join('')}$('#app').innerHTML='<div class="empty">正在加载…</div>';
 try{await pages[state.active]()}catch(e){if(version!==state.viewVersion)return;$('#app').innerHTML=`<div class="card empty"><strong>暂时无法加载</strong>${esc(e.message)}<p>${button('重试','reload')}</p></div>`}
}
const card=(title,actions,body)=>`<div class="card"><div class="cardhead"><h2>${title}</h2><div class="actions" style="margin:0">${actions||''}</div></div>${body}</div>`;
const pages={
 async overview(){const d=await api('/dashboard');$('#app').innerHTML=`<div class="stats">${[['题库试题',d.questions,'来自资源中心考试题库'],['已建试卷',d.papers,'配置规则，灵活组卷'],['考试计划',d.schedules,'累计答卷 '+d.attempts+' 份'],['已发证书',d.certificates,'待阅卷 '+d.pending_grading+' 份']].map(([t,n,s])=>`<div class="stat"><span>${t}</span><strong>${n}</strong><small>${s}</small></div>`).join('')}</div>${card('考试管理流程','',`<div class="steps">${[['01','题库查询','从资源中心考试题库查询试题，也可新增试题并同步入库。','questions'],['02','配置试卷','手动选题或按题型比例随机抽题，整卷总分 100 分。','papers'],['03','组织考试','指定培训班或人员，发布定时考试计划。','schedules'],['04','答题与阅卷','自动评分客观题，主观题由人工批阅。','grading'],['05','错题复盘','按课程和题型回顾个人错题与解析。','wrong'],['06','发放证书','合格自动生成证书，支持 PDF 和图片下载。','certificates']].map(([n,t,s,p])=>`<div class="step"><span class="number">STEP ${n}</span><h3>${t}</h3><p>${s}</p>${available().includes(p)?button('进入模块 →','navigate',p):''}</div>`).join('')}</div>`)}<div class="note">当前数据属于你登录的企业。题库统一来源为资源中心（模块一）考试题库；已发布考试保留题目快照。</div>`},
 async questions(){state.data.questions=await api('/questions');$('#app').innerHTML=card('题库查询',author()?button('＋ 新增试题','question-new','','btn'):'',`<div class="note">数据来源：模块一 资源中心 · 考试题库。可在此检索试题；新增的试题会同步写入资源中心考试题库。</div><div class="toolbar"><input id="q-keyword" placeholder="搜索题干或标签"><select id="q-type"><option value="">全部题型</option>${Object.entries(types).map(([v,n])=>`<option value="${v}">${n}</option>`)}</select><select id="q-difficulty"><option value="">全部难度</option>${Object.entries({easy:'简单',medium:'中等',hard:'困难'}).map(([v,n])=>`<option value="${v}">${n}</option>`)}</select><select id="q-status"><option value="">全部状态</option>${['enabled','disabled'].map(v=>`<option value="${v}">${statusNames[v]}</option>`)}</select><span class="spacer"></span><span class="meta">共 ${state.data.questions.length} 道试题，仅启用且答案完整的试题可用于组卷</span></div><div id="question-table"></div>`);['#q-keyword','#q-type','#q-difficulty','#q-status'].forEach(x=>$(x).oninput=renderQuestions);renderQuestions()},
 async papers(){state.data.papers=await api('/papers');$('#app').innerHTML=card('试卷管理',author()?button('手动组卷','paper-new')+' '+button('随机组卷','paper-auto','','btn'):'',`<div class="note">已发布试卷锁定参数。整份试卷总分统一为 100 分，选题与分值合计不可超过 100 分。随机组卷按每种题型的数量、分值和工种范围抽取资源中心考试题库中的试题。</div>${table(['试卷名称','考试类型','题量 / 总分','及格分','时长 / 重考','状态','操作'],state.data.papers.map(p=>`<tr><td>${esc(p.name)}<div class="meta">${esc(p.course_name||'未关联课程')}${(p.work_types||[]).length?' · 工种：'+esc((p.work_types||[]).join('、')):''}${(p.cert_names||[]).length?' · 发放资格证：'+esc((p.cert_names||[]).join('、')):''}</div></td><td>${exams[p.exam_type]}</td><td>${p.question_count} 题 / ${p.total_score} 分</td><td>${p.pass_score}</td><td>${p.duration_min} 分钟 / ${p.retry_limit} 次</td><td>${badge(p.status)}</td><td><div class="actions">${author()&&p.status==='draft'?button('编辑参数','paper-edit',p.id)+button('选题与分值','paper-questions',p.id)+button('发布','paper-publish',p.id):state.session.role!=='viewer'?button('查看题目','paper-view',p.id):''}</div></td></tr>`))}`)},
 async schedules(){const [rows,papers,members,classes,courseExams]=await Promise.all([api('/admin/exam-schedules'),api('/papers'),api('/admin/members'),api('/admin/classes'),api('/course-exams').catch(()=>[])]);Object.assign(state.data,{schedules:rows,papers,members,classes,courseExams});$('#app').innerHTML=card('考试安排',button('维护培训班','class-new')+' '+button('＋ 新建考试计划','schedule-new','','btn'),`<div class="note">发布后为指定人员生成任务；学员从个人门户的「考试与证书」进入答题。时间按当前设备时区显示。</div>${table(['考试计划','开考 / 结束时间','参考范围','完成情况','状态','操作'],rows.map(s=>`<tr><td>${esc(s.name)}</td><td>${date(s.start_at)}<br>${date(s.end_at)}</td><td>${s.scope_type==='class'?'培训班':'指定人员'}</td><td>${s.finished} / ${s.participants} 人</td><td>${badge(s.status)}</td><td>${s.status==='draft'?button('发布计划','schedule-publish',s.id):'已生成学员任务'}</td></tr>`))}${courseExamsSection(courseExams,papers)}`)},
 async grading(){const [pending,results]=await Promise.all([author()?api('/admin/grading'):Promise.resolve([]),api('/admin/results')]);state.data.results=results;$('#app').innerHTML=card('主观题待阅卷',button('刷新','reload'),table(['考试名称','学员','客观题得分','交卷时间','操作'],pending.map(a=>`<tr><td>${esc(a.exam)}</td><td>${esc(a.learner)}</td><td>${a.objective_score}</td><td>${date(a.submitted_at)}</td><td>${button('开始阅卷','grade',a.id)}</td></tr>`)))+card('考试成绩','',table(['考试名称','学员','状态','总分','结果','切屏次数'],results.map(a=>`<tr><td>${esc(a.exam)}</td><td>${esc(a.learner)}</td><td>${badge(a.status)}</td><td>${a.score??'—'}</td><td>${a.is_pass===true?'合格':a.is_pass===false?'未合格':'待评定'}</td><td>${a.switch_count}</td></tr>`)))},
 async certificates(){
   const rows=await api('/admin/certs');
   renderCerts(rows,'证书管理');
   if(admin()){
     $('#app').insertAdjacentHTML('beforeend',card('证书模板','配置结业 / 岗位能力两类证书的标题、正文与落款，学员考试合格后自动套用生成。',`<div class="toolbar">${button('编辑证书模板','cert-template-editor','')}</div><div class="meta">模板正文支持占位符：{learner} 学员 · {title} 考试 · {score} 成绩 · {cert_no} 证书编号 · {date} 签发日期。联合认证证书由外部机构签发，不套用平台模板。</div>`));
   }
 },
 async mycerts(){const rows=await api('/my/certs');renderCerts(rows,'我的证书 · 个人考试档案')},
 async issuers(){const [issuers,logs]=await Promise.all([api('/admin/cert-issuers'),api('/admin/cert-sync-logs')]);Object.assign(state.data,{issuers,logs});$('#app').innerHTML=card('合作发证机构',button('＋ 配置机构','issuer-new','','btn'),`<div class="note">需要合作机构提供真实接口地址和密钥。平台考核合格证明不代表外部机构已认证；外部证书以成功回执为准。当前对接协议：HTTPS POST JSON，返回 ext_cert_no；其他协议需另行适配。</div>${table(['机构名称','接口地址','状态','操作'],issuers.map(i=>`<tr><td>${esc(i.name)}</td><td>${esc(i.api_url)}</td><td>${i.enabled?'已启用':'未启用'}</td><td>${button('编辑','issuer-edit',i.id)} ${button('测试连接','issuer-test',i.id)}</td></tr>`))}`)+card('联合认证同步记录','',table(['证书 ID','发证机构','状态','外部证书编号','最近同步','操作'],logs.map(l=>`<tr><td>${l.cert_id}</td><td>${esc(l.issuer)}</td><td>${badge(l.status)}</td><td>${esc(l.ext_cert_no||'尚未签发')}</td><td>${date(l.synced_at)}</td><td>${l.status!=='success'?button('同步 / 重试','issuer-retry',l.id):''}</td></tr>`)))},
 async myexams(){state.data.tasks=await api('/my/exams');$('#app').innerHTML=`<div class="cardhead" style="margin-bottom:20px"><h2>我的考试</h2>${button('刷新任务','reload')}</div><div class="examcards">${state.data.tasks.map(t=>{const open=Date.now()>=parseUTC(t.start_at)&&Date.now()<parseUTC(t.end_at);const can=t.result_status!=='submitted'&&open&&(t.result_status==='in_progress'||t.attempt_used<=t.retry_limit);return `<article class="examcard">${badge(t.status)}<h3>${esc(t.name)}</h3><div class="meta">${esc(t.paper)}<br>开始：${date(t.start_at)}<br>结束：${date(t.end_at)}<br>时长 ${t.duration_min} 分钟 · 已用 ${t.attempt_used} / ${t.retry_limit+1} 次</div><p>${t.score===null?'成绩：尚未出分':'成绩：'+t.score+' 分'}</p><div class="actions">${t.attempt_id?button('查看结果','result',t.attempt_id):''}${can?button(t.result_status==='in_progress'?'继续答题':t.attempt_used?'再次考试':'开始考试','start',t.id,'btn'):''}</div></article>`}).join('')||'<div class="card empty"><strong>暂无考试任务</strong>管理员发布考试计划后，可在这里查看并参加。</div>'}</div>`},
 async wrong(){const rows=await api('/my/wrong-questions');state.data.wrong=rows;$('#app').innerHTML=card('个人错题本','',`<div class="toolbar"><input id="w-course" placeholder="按课程名称筛选"><select id="w-type"><option value="">全部题型</option>${Object.entries(types).map(([v,n])=>`<option value="${v}">${n}</option>`)}</select></div><div id="wrong-list"></div>`);$('#w-course').oninput=renderWrong;$('#w-type').onchange=renderWrong;renderWrong()}
};
function renderQuestions(){const keyword=$('#q-keyword').value.trim().toLowerCase(),qt=$('#q-type').value,df=$('#q-difficulty').value,st=$('#q-status').value;const rows=state.data.questions.filter(q=>(!qt||q.q_type===qt)&&(!st||q.status===st)&&(!df||q.difficulty===df)&&(!keyword||(q.stem+JSON.stringify(q.tags)).toLowerCase().includes(keyword)));$('#question-table').innerHTML=table(['题型','题干 / 标签','难度','状态','操作'],rows.map(q=>`<tr><td>${types[q.q_type]}</td><td><div class="stem">${esc(q.stem)}</div><div class="meta">${esc(Object.values(q.tags||{}).flat().join(' · '))}</div></td><td>${{easy:'简单',medium:'中等',hard:'困难'}[q.difficulty]}</td><td>${badge(q.status)}</td><td><div class="actions">${button('详情','question-view',q.id)}${author()?button('编辑','question-edit',q.id):''}</div></td></tr>`))}
function renderWrong(){const rows=state.data.wrong.filter(w=>(!$('#w-type').value||w.q_type===$('#w-type').value)&&(!$('#w-course').value||(w.course_name||'').includes($('#w-course').value)));$('#wrong-list').innerHTML=rows.map(w=>`<div class="question"><div class="cardhead"><span class="meta">${types[w.q_type]} · ${esc(w.course_name||'未关联课程')}</span>${button('标记已掌握','mastered',w.id)}</div><h3 style="margin-top:12px">${esc(w.stem)}</h3>${(w.options||[]).map(o=>`<div>${esc(o.key)}. ${esc(o.text)}</div>`).join('')}<div class="note"><b>正确答案 / 要点</b>：${answerText(w.correct_answer)}<br><b>解析</b>：${esc(w.analysis||'暂无解析')}</div></div>`).join('')||'<div class="empty">暂无符合条件的错题</div>'}
function renderCerts(rows,title){$('#app').innerHTML=card(title,'',table(['证书名称','获得人','证书类型','证书编号','成绩 / 签发时间','操作'],rows.map(c=>`<tr><td>${esc(c.title)}</td><td>${esc(c.learner)}</td><td>${certTypes[c.cert_type]}${c.cert_type==='joint'?'<div class="meta">外部认证以机构回执为准</div>':''}</td><td>${esc(c.cert_no)}</td><td>${c.score} 分<br>${date(c.issued_at)}</td><td>${button('预览','cert-preview',c.id)} ${button('PDF','cert-pdf',c.id)} ${button('图片','cert-png',c.id)}</td></tr>`)))}
export const answerText=a=>esc(Array.isArray(a)?a.map(x=>typeof x==='boolean'?(x?'正确':'错误'):x).join('、'):typeof a==='object'&&a?a.points||JSON.stringify(a):a||'未作答');
const parseUTC=s=>new Date(/[zZ]|[+-]\d\d:\d\d$/.test(s)?s:s+'Z').getTime();
let timer;
async function start(id){const a=await post(`/my/exams/${id}/start`);state.attempt=a;state.drafts={...a.answers};state.errors={};parent.postMessage({type:'training-exam:attempt',active:true},location.origin);$('#nav').hidden=true;$('#app').innerHTML=`<div class="examhead"><div><b>在线考试</b><div class="meta">答案自动保存${a.anti_switch?' · 切屏会记录次数':''}</div></div><span id="timer" class="timer"></span>${button('提交答卷','submit','','btn')}</div>${a.questions.map((q,i)=>`<div class="question"><div class="meta">第 ${i+1} 题 / ${a.questions.length} · ${types[q.q_type]} · ${q.score} 分 <span id="saved-${q.id}" class="saved">${a.answers[q.id]!==undefined?'已恢复保存的答案':''}</span></div><h3>${esc(q.stem)}</h3>${['single','multi','judge'].includes(q.q_type)?(q.q_type==='judge'?[{key:true,text:'正确'},{key:false,text:'错误'}]:q.options).map(o=>`<label class="option"><input type="${q.q_type==='multi'?'checkbox':'radio'}" name="answer-${q.id}" data-question="${q.id}" value="${esc(String(o.key))}" ${(a.answers[q.id]||[]).includes(o.key)?'checked':''}> ${q.q_type==='judge'?'':esc(o.key)+'. '}${esc(o.text)}</label>`).join(''):`<textarea data-question="${q.id}" maxlength="20000" placeholder="请输入你的答案">${esc(a.answers[q.id]||'')}</textarea>`}</div>`).join('')}`;
 const offset=parseUTC(a.server_now)-Date.now();const tick=()=>{const seconds=Math.max(0,Math.floor((parseUTC(a.deadline_at)-Date.now()-offset)/1000));if($('#timer'))$('#timer').textContent=`${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,'0')}`;if(seconds===0){clearInterval(timer);submit(true)}};tick();timer=setInterval(tick,1000);
 $('#app').querySelectorAll('[data-question]').forEach(el=>el.addEventListener(el.tagName==='TEXTAREA'?'input':'change',()=>save(el)));
}
const queues={};
function save(el){const a=state.attempt;if(!a)return;const id=Number(el.dataset.question),q=a.questions.find(q=>q.id===id);let value;if(q.q_type==='essay'||q.q_type==='case')value=el.value;else value=[...document.querySelectorAll(`[data-question="${id}"]:checked`)].map(x=>q.q_type==='judge'?x.value==='true':x.value);state.drafts[id]=value;const indicator=$(`#saved-${id}`);indicator.textContent='保存中…';indicator.className='saved';const work=(queues[id]||Promise.resolve()).catch(()=>{}).then(()=>post(`/my/exams/attempts/${a.attempt_id}/answers`,{question_id:id,user_answer:value}));queues[id]=work;state.pending.add(work);work.then(()=>{delete state.errors[id];if(indicator)indicator.textContent='已保存'},e=>{state.errors[id]=value;if(indicator){indicator.textContent='保存失败，请检查网络后重试';indicator.className='saved failure'}toast(e.message)}).finally(()=>state.pending.delete(work))}
let submitting=false;
async function submit(auto=false){if(!state.attempt||submitting)return;if(!auto&&!confirm('确定提交答卷吗？提交后本次答案无法修改。'))return;submitting=true;try{await Promise.allSettled([...state.pending]);if(!auto&&Object.keys(state.errors).length){for(const [id,value]of Object.entries(state.errors))await post(`/my/exams/attempts/${state.attempt.attempt_id}/answers`,{question_id:Number(id),user_answer:value});state.errors={}}const id=state.attempt.attempt_id;await post(`/my/exams/attempts/${id}/submit`);clearInterval(timer);state.attempt=null;parent.postMessage({type:'training-exam:attempt',active:false},location.origin);$('#nav').hidden=false;await load('myexams');await result(id)}catch(e){toast(e.message);if(auto)setTimeout(()=>submit(true),5000)}finally{submitting=false}}
async function result(id){const r=await api(`/my/exams/attempts/${id}/result`);modal('考试结果',`<div style="text-align:center"><div>${badge(r.status)}</div><div class="score">${r.status==='graded'?r.total_score+' 分':'待阅卷'}</div><p>${r.status==='graded'?(r.is_pass?'恭喜，考试合格。证书可在「我的证书」查看。':'本次未达到及格线，可在错题本中复习。'):'客观题得分 '+r.objective_score+'，主观题等待管理员批阅。'}</p><p class="meta">切屏记录：${r.switch_count} 次</p></div>`) }
async function download(id,format){
  if(format==='pdf'){
    const r=await fetch(`/training-exam/api/my/certs/${id}/download`,{headers:{Authorization:state.token,'tenant-id':String(state.tenant)}});
    if(!r.ok)throw new Error('证书下载失败');
    saveBlob(await r.blob(),'certificate.pdf');
    return;
  }
  const c=await api(`/my/certs/${id}`);
  const t=c.tpl||{};
  const canvas=document.createElement('canvas');
  canvas.width=1400;canvas.height=950;
  const g=canvas.getContext('2d');
  g.fillStyle='#fffdf5';g.fillRect(0,0,1400,950);
  g.strokeStyle='#19364e';g.lineWidth=9;g.strokeRect(35,35,1330,880);
  g.lineWidth=2;g.strokeRect(53,53,1294,844);
  g.fillStyle='#19364e';g.textAlign='center';
  g.font='bold 64px Microsoft YaHei';g.fillText(t.title||'考试合格证书',700,210);
  g.font='32px Microsoft YaHei';
  const lines=c.title.match(/.{1,30}/g)||[];
  lines.forEach((l,i)=>g.fillText(l,700,305+i*44));
  g.font='28px Microsoft YaHei';
  const bodyLines=(t.body||`兹证明 ${c.learner} 已通过考核，成绩 ${c.score} 分。`).split('\n');
  let y=480;
  bodyLines.forEach(para=>{(para.match(/.{1,40}/g)||['']).forEach(seg=>{g.fillText(seg,700,y);y+=42})});
  g.font='25px Microsoft YaHei';
  g.fillText(certTypes[c.cert_type],700,600);
  if(c.cert_type==='joint')g.fillText('外部认证以合作机构签发回执为准',700,650);
  g.fillText(c.cert_no,700,755);
  g.fillText((t.org||'云培训学院')+' · '+date(c.issued_at).split(' ')[0],700,820);
  canvas.toBlob(blob=>saveBlob(blob,c.cert_no+'.png'),'image/png');
}
function saveBlob(blob,name){const u=URL.createObjectURL(blob),a=document.createElement('a');a.href=u;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(u),1000)}
const actions={
  ...editorActions,
  navigate:load,
  reload:()=>load(),
  start,
  submit:()=>submit(false),
  result,
  'cert-pdf':id=>download(id,'pdf'),
  'cert-png':id=>download(id,'png'),
  'cert-preview':async id=>{
    const c=await api(`/my/certs/${id}`);
    const t=c.tpl||{};
    modal('证书预览',`<div class="certificate"><h2>${esc(t.title||'考试合格证书')}</h2><h3>${esc(c.title)}</h3>${(t.body||`兹证明 ${esc(c.learner)} 已通过考核，成绩 ${c.score} 分。`).split('\n').map(l=>`<p>${esc(l)}</p>`).join('')}<p>${certTypes[c.cert_type]}</p><small>${esc(c.cert_no)}<br>${esc(t.org||'云培训学院')} · ${date(c.issued_at)}</small>${c.cert_type==='joint'?'<p class="meta">外部认证以合作机构发证回执为准。</p>':''}</div>`);
  },
  'cert-template-editor':async()=>{
    const list=await api('/admin/cert-templates');
    const box=(t)=>`<div class="field full"><label>${esc(t.tpl_name)}（${t.tpl_type}）${t.enabled?' · 已启用':' · 已停用'}</label><input id="ct-title-${t.id}" value="${esc(t.title)}" maxlength="200"><textarea id="ct-body-${t.id}" rows="3" maxlength="2000">${esc(t.body_text)}</textarea><input id="ct-org-${t.id}" value="${esc(t.org_name)}" maxlength="100"><label class="inline"><input type="checkbox" id="ct-enabled-${t.id}" ${t.enabled?'checked':''}> 启用该模板（停用后该类证书回退默认文案）</label></div>`;
    const d=modal('证书模板',`<p class="meta">正文占位符：{learner} 学员 · {title} 考试 · {score} 成绩 · {cert_no} 证书编号 · {date} 签发日期</p>${list.map(box).join('')}`,'保存模板',async()=>{
      for(const t of list){
        await api(`/admin/cert-templates/${t.id}`,{method:'PUT',body:JSON.stringify({title:$(`#ct-title-${t.id}`,d).value.trim(),body_text:$(`#ct-body-${t.id}`,d).value.trim(),org_name:$(`#ct-org-${t.id}`,d).value.trim(),enabled:$(`#ct-enabled-${t.id}`,d).checked})});
      }
      toast('证书模板已保存');
      await load();
    });
  },
  mastered:async id=>{await post(`/my/wrong-questions/${id}/mastered`);await load()},
};
document.addEventListener('click',async e=>{const b=e.target.closest('[data-action]');if(!b)return;const fn=actions[b.dataset.action];if(!fn)return;b.disabled=true;try{await fn(b.dataset.id)}catch(e){toast(e.message)}finally{b.disabled=false}});
document.addEventListener('visibilitychange',()=>{if(document.hidden&&state.attempt?.anti_switch)post(`/my/exams/attempts/${state.attempt.attempt_id}/switch`).catch(()=>{})});
window.addEventListener('beforeunload',e=>{if(state.attempt){e.preventDefault();e.returnValue='考试进行中，请先交卷'}});
let initialized=false;
window.addEventListener('message',async e=>{if(e.origin!==location.origin||e.source!==parent||e.data?.type!=='training-exam:session')return;const {token,tenantId,dark}=e.data;document.body.classList.toggle('dark',!!dark);if(!token||!tenantId)return;state.token=token.startsWith('Bearer ')?token:'Bearer '+token;state.tenant=tenantId;if(initialized)return;try{state.session=await api('/session');initialized=true;clearInterval(handshake);$('#identity').textContent=state.session.name+' · '+({admin:'考试管理员',teacher:'讲师',reviewer:'审核员',viewer:'只读访问',learner:'学员'}[state.session.role]);document.body.dataset.mode=state.mode;if(state.embedded)document.body.classList.add('embedded');await load(new URLSearchParams(location.search).get('page')||(state.mode==='learner'?'myexams':'overview'))}catch(e){$('#app').innerHTML=`<div class="empty">${esc(e.message)}</div>`}});
const announce=()=>parent.postMessage({type:'training-exam:ready'},location.origin);announce();const handshake=setInterval(announce,2000);setTimeout(()=>{if(!initialized)$('#app').innerHTML='<div class="empty"><strong>请从平台登录后进入</strong>企业后台：云培训学院 → 考试与认证<br>个人门户：云培训学院 → 考试与证书</div>'},8000);
