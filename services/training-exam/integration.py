"""Platform integration and validated extensions for the examination engine."""
from __future__ import annotations
import asyncio
import ipaddress
import socket
from urllib.parse import urlparse
from contextlib import asynccontextmanager
from pydantic import BaseModel, Field


def install(ctx):
    globals().update(ctx)

    class TrainingClass(Base):
        __tablename__ = "training_classes"
        id: Mapped[int] = mapped_column(primary_key=True)
        name: Mapped[str] = mapped_column(String(200))
        user_ids: Mapped[str] = mapped_column(Text)

    ctx["TrainingClass"] = TrainingClass
    globals()["TrainingClass"] = TrainingClass

    def utc_naive(value):
        return value.astimezone(timezone.utc).replace(tzinfo=None) if value.tzinfo else value

    def sync_members(db, actor):
        rows = platform_get("/system/user/simple-list", actor.token, actor.tenant_id)
        result = []
        for item in rows:
            uid = int(item["id"])
            name = item.get("nickname") or item.get("username") or str(uid)
            member = db.get(User, uid)
            if not member: db.add(User(id=uid, name=name, role="learner"))
            elif member.name != name: member.name = name
            result.append({"id": uid, "name": name, "dept_id": item.get("deptId")})
        db.commit()
        return result

    def validate_given(iq, answer):
        if iq.q_type in {"single", "multi", "judge"}:
            if not isinstance(answer, list): raise HTTPException(422,"选项答案格式错误")
            if iq.q_type == "judge":
                if len(answer)>1 or any(type(x) is not bool for x in answer): raise HTTPException(422,"判断题答案格式错误")
            else:
                keys = {x["key"] for x in parse(iq.options, [])}
                if any(not isinstance(x,str) or x not in keys for x in answer) or len(set(answer))!=len(answer): raise HTTPException(422,"存在无效选项")
                if iq.q_type=="single" and len(answer)>1: raise HTTPException(422,"单选题只能选择一项")
        elif not isinstance(answer, str) or len(answer)>20000: raise HTTPException(422,"主观题答案须为不超过 20000 字的文本")

    ctx.update(utc_naive=utc_naive,sync_members=sync_members,validate_given=validate_given)

    # One worker per service; serialize writes and deadline finalization per enterprise.
    locks = {}
    def lock(tid): return locks.setdefault(str(tid), asyncio.Lock())

    @app.middleware("http")
    async def transaction_boundary(request, call_next):
        if request.url.path.removeprefix(request.scope.get("root_path", "")).startswith("/api/"):
            async with lock(request.headers.get("tenant-id","unknown")):
                response = await call_next(request)
        else: response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Content-Security-Policy"] = "frame-ancestors 'self'"
        return response

    async def expire_attempts():
        while True:
            for path in DATA.glob("tenant-*.db"):
                try:
                    tid = int(path.stem.split('-')[1])
                    async with lock(tid):
                        with Session(tenant_engine(tid),expire_on_commit=False) as db:
                            for attempt in db.scalars(select(ExamAttempt).where(ExamAttempt.status=="in_progress",ExamAttempt.deadline_at<=now())):
                                finalize(db,attempt,"auto")
                            db.commit()
                except Exception:
                    import logging
                    logging.exception("Exam deadline finalization failed")
            await asyncio.sleep(5)

    @asynccontextmanager
    async def lifespan(_):
        task = asyncio.create_task(expire_attempts())
        yield
        task.cancel()
        try: await task
        except asyncio.CancelledError: pass
    app.router.lifespan_context=lifespan

    @app.get("/health")
    def health(): return {"status":"ok"}

    @app.get("/api/session")
    def session_info(actor: Actor = Depends(current_user)):
        return ok({"id":actor.id,"name":actor.name,"role":actor.role,"super_admin":actor.super_admin,"tenant_id":actor.tenant_id})

    @app.get("/api/admin/members")
    def members(db: Session = Depends(db_session),actor: Actor=Depends(require("admin"))):
        return ok(sync_members(db,actor))

    class ClassIn(BaseModel):
        name: str = Field(min_length=2,max_length=200)
        user_ids: list[int] = Field(min_length=1)

    globals()["ClassIn"] = ClassIn

    @app.get("/api/admin/classes")
    def classes(db:Session=Depends(db_session),actor:Actor=Depends(require("admin"))):
        return ok([{"id":g.id,"name":g.name,"user_ids":parse(g.user_ids,[])} for g in db.scalars(select(TrainingClass))])

    @app.post("/api/admin/classes")
    def create_class(body:ClassIn,db:Session=Depends(db_session),actor:Actor=Depends(require("admin"))):
        users=sync_members(db,actor)
        if set(body.user_ids)-{u["id"] for u in users}: raise HTTPException(422,"培训班包含非当前企业用户")
        group=TrainingClass(name=body.name,user_ids=json.dumps(sorted(set(body.user_ids))))
        db.add(group);db.commit();return ok({"id":group.id})

    @app.put("/api/questions/{qid}")
    def update_question(qid:int,body:QuestionIn,db:Session=Depends(db_session),actor:Actor=Depends(require("admin","teacher"))):
        q=db.get(Question,qid)
        if not q:raise HTTPException(404,"试题不存在")
        if actor.role=="teacher" and q.creator_id!=actor.id: raise HTTPException(403,"只能编辑自己的试题")
        if q.status=="pending":raise HTTPException(409,"待审核试题不能编辑")
        for key in ["q_type","stem","analysis","difficulty"]:setattr(q,key,getattr(body,key))
        q.options=json.dumps(body.options,ensure_ascii=False);q.answer=json.dumps(body.answer,ensure_ascii=False);q.tags=json.dumps(body.tags,ensure_ascii=False)
        q.status="draft";db.commit();return ok(question_view(q,True),"已保存草稿，需重新提交审核")

    @app.get("/api/questions/{qid}/reviews")
    def reviews(qid:int,db:Session=Depends(db_session),actor:Actor=Depends(require("admin","teacher","reviewer"))):
        return ok([{"action":r.action,"opinion":r.opinion,"operator":db.get(User,r.operator_id).name,"created_at":r.created_at} for r in db.scalars(select(QuestionReview).where(QuestionReview.question_id==qid).order_by(QuestionReview.id.desc()))])

    @app.get("/api/papers/{pid}")
    def paper_detail(pid:int,db:Session=Depends(db_session),actor:Actor=Depends(require("admin","teacher","reviewer"))):
        p=db.get(Paper,pid)
        if not p:raise HTTPException(404,"试卷不存在")
        return ok({"id":p.id,"questions":[{**question_view(db.get(Question,x.question_id),True),"score":x.score} for x in sorted(p.questions,key=lambda x:x.sort_no)]})

    @app.put("/api/papers/{pid}")
    def update_paper(pid:int,body:PaperIn,db:Session=Depends(db_session),actor:Actor=Depends(require("admin","teacher"))):
        p=db.get(Paper,pid)
        if not p or p.status!="draft":raise HTTPException(409,"仅草稿试卷可修改参数")
        for key,value in body.model_dump().items():setattr(p,key,value)
        db.commit();return ok()

    @app.put("/api/papers/{pid}/questions")
    def replace_questions(pid:int,items:list[dict[str,Any]],db:Session=Depends(db_session),actor:Actor=Depends(require("admin","teacher"))):
        p=db.get(Paper,pid)
        if not p or p.status!="draft":raise HTTPException(409,"仅草稿试卷可编辑")
        p.questions.clear();db.flush()
        return add_paper_questions(pid,items,db,actor)

    @app.get("/api/admin/results")
    def results(db:Session=Depends(db_session),actor:Actor=Depends(require("admin","teacher","reviewer","viewer"))):
        return ok([{"id":a.id,"learner":db.get(User,a.user_id).name,"exam":db.get(ExamSchedule,a.schedule_id).name,"status":a.status,"score":a.total_score if a.status=="graded" else None,"is_pass":a.is_pass,"switch_count":a.switch_count,"submitted_at":a.submitted_at} for a in db.scalars(select(ExamAttempt).order_by(ExamAttempt.id.desc()))])

    @app.get("/api/admin/certs")
    def certs(db:Session=Depends(db_session),actor:Actor=Depends(require("admin","teacher","reviewer","viewer"))):
        return ok([cert_dict(c,db) for c in db.scalars(select(Certificate).order_by(Certificate.id.desc()))])

    @app.get("/api/my/certs/{cid}")
    def cert_data(cid:int,db:Session=Depends(db_session),actor:Actor=Depends(current_user)):
        c=db.get(Certificate,cid)
        if not c or (actor.role not in {"admin","teacher","reviewer","viewer"} and c.user_id!=actor.id):raise HTTPException(404,"证书不存在")
        return ok(cert_dict(c,db))

    # Replace original demo responses; never report mock external certification as real.
    replaced={"/api/my/certs/{cid}/download","/api/admin/cert-issuers","/api/admin/cert-issuers/{iid}/test","/api/admin/cert-sync-logs"}
    app.router.routes[:]=[r for r in app.router.routes if getattr(r,"path",None) not in replaced]

    @app.get("/api/my/certs/{cid}/download")
    def download(cid:int,db:Session=Depends(db_session),actor:Actor=Depends(current_user)):
        from reportlab.pdfbase.cidfonts import UnicodeCIDFont
        c=db.get(Certificate,cid)
        if not c or (actor.role not in {"admin","teacher","reviewer","viewer"} and c.user_id!=actor.id):raise HTTPException(404,"证书不存在")
        d=cert_dict(c,db);buf=io.BytesIO();pdf=canvas.Canvas(buf,pagesize=landscape(A4));w,h=landscape(A4)
        pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"));pdf.setStrokeColor(colors.HexColor("#173f5f"));pdf.setLineWidth(4);pdf.rect(24,24,w-48,h-48)
        pdf.setFillColor(colors.HexColor("#173f5f"));pdf.setFont("STSong-Light",36);pdf.drawCentredString(w/2,h-105,"考试合格证书")
        pdf.setFont("STSong-Light",20)
        title=d["title"]
        for i in range(0,len(title),28):pdf.drawCentredString(w/2,h-155-(i//28)*25,title[i:i+28])
        pdf.setFont("STSong-Light",18);pdf.drawCentredString(w/2,250,f"兹证明 {d['learner']} 已通过考核，成绩 {d['score']:g} 分。")
        pdf.setFont("STSong-Light",13);pdf.drawCentredString(w/2,160,{"graduate":"结业证书","competence":"岗位能力认证证书","joint":"联合认证申请对应考核合格证明"}[c.cert_type])
        pdf.drawCentredString(w/2,110,d["cert_no"]);pdf.drawCentredString(w/2,80,f"云培训学院    签发日期：{d['issued_at']:%Y-%m-%d}")
        pdf.save();return Response(buf.getvalue(),media_type="application/pdf",headers={"Content-Disposition":f'attachment; filename="{c.cert_no}.pdf"'})

    def only_super(actor):
        if not actor.super_admin:raise HTTPException(403,"仅超级管理员可管理合作发证机构")

    def validate_url(url):
        parsed=urlparse(url)
        if parsed.scheme!="https" or not parsed.hostname or parsed.username or parsed.password:raise HTTPException(422,"机构接口必须是 HTTPS 地址")
        try:
            ips={entry[4][0] for entry in socket.getaddrinfo(parsed.hostname,parsed.port or 443)}
        except OSError:raise HTTPException(422,"无法解析机构接口域名")
        if any(not ipaddress.ip_address(ip).is_global for ip in ips):raise HTTPException(422,"机构接口须为公网服务地址")

    class IssuerIn(BaseModel):
        name:str=Field(min_length=2,max_length=100)
        api_url:str=Field(max_length=300)
        api_key:str=Field("",max_length=200)
        enabled:bool=False
        issue_on_pass:bool=True

    globals()["IssuerIn"] = IssuerIn

    @app.get("/api/admin/cert-issuers")
    def issuer_list(db:Session=Depends(db_session),actor:Actor=Depends(current_user)):
        only_super(actor)
        return ok([{"id":x.id,"name":x.name,"api_url":x.api_url,"enabled":x.enabled,"issue_on_pass":x.issue_on_pass,"has_key":bool(x.api_key)} for x in db.scalars(select(CertIssuer))])

    @app.post("/api/admin/cert-issuers")
    def create_issuer(body:IssuerIn,db:Session=Depends(db_session),actor:Actor=Depends(current_user)):
        only_super(actor);validate_url(body.api_url)
        if not body.api_key:raise HTTPException(422,"请填写机构接口密钥")
        issuer=CertIssuer(**body.model_dump());db.add(issuer);db.commit();return ok({"id":issuer.id})

    @app.put("/api/admin/cert-issuers/{iid}")
    def update_issuer(iid:int,body:IssuerIn,db:Session=Depends(db_session),actor:Actor=Depends(current_user)):
        only_super(actor);validate_url(body.api_url);issuer=db.get(CertIssuer,iid)
        if not issuer:raise HTTPException(404,"机构不存在")
        for key,value in body.model_dump().items():
            if key!="api_key" or value:setattr(issuer,key,value)
        db.commit();return ok()

    @app.post("/api/admin/cert-issuers/{iid}/test")
    def probe(iid:int,db:Session=Depends(db_session),actor:Actor=Depends(current_user)):
        only_super(actor);issuer=db.get(CertIssuer,iid)
        if not issuer:raise HTTPException(404,"机构不存在")
        validate_url(issuer.api_url)
        try:r=httpx.options(issuer.api_url,headers={"Authorization":f"Bearer {issuer.api_key}"},timeout=10,follow_redirects=False)
        except httpx.HTTPError:raise HTTPException(502,"机构接口无法连接")
        if r.status_code>=400:raise HTTPException(502,f"机构返回 HTTP {r.status_code}，请核对其接口协议")
        return ok({"status":r.status_code},"机构接口可访问；发证需以实际同步回执为准")

    @app.get("/api/admin/cert-sync-logs")
    def logs(db:Session=Depends(db_session),actor:Actor=Depends(require("admin"))):
        return ok([{"id":x.id,"cert_id":x.cert_id,"issuer":db.get(CertIssuer,x.issuer_id).name,"status":x.status,"ext_cert_no":x.ext_cert_no,"response":parse(x.response),"synced_at":x.synced_at} for x in db.scalars(select(CertSyncLog).order_by(CertSyncLog.id.desc()))])

    @app.post("/api/admin/cert-sync-logs/{lid}/retry")
    def retry_sync(lid:int,db:Session=Depends(db_session),actor:Actor=Depends(current_user)):
        only_super(actor);log=db.get(CertSyncLog,lid)
        if not log:raise HTTPException(404,"同步记录不存在")
        if log.status=="success":return ok(message="已同步成功，无需重复发证")
        issuer=db.get(CertIssuer,log.issuer_id)
        if not issuer.enabled:raise HTTPException(422,"请先启用合作机构")
        validate_url(issuer.api_url)
        try:
            r=httpx.post(issuer.api_url,json=parse(log.request_body),headers={"Authorization":f"Bearer {issuer.api_key}","Idempotency-Key":f"exam-{actor.tenant_id}-{log.cert_id}-{issuer.id}"},timeout=15,follow_redirects=False)
            payload=r.json()
            if r.status_code not in {200,201} or not payload.get("ext_cert_no"):raise ValueError("机构未返回有效的 ext_cert_no 发证回执")
            log.status="success";log.ext_cert_no=str(payload["ext_cert_no"]);log.response=json.dumps({"ext_cert_no":log.ext_cert_no},ensure_ascii=False)
        except (httpx.HTTPError,ValueError):
            log.status="failed";log.response=json.dumps({"message":"同步未成功，请核对机构协议及服务状态"},ensure_ascii=False)
        log.synced_at=now();db.commit();return ok({"status":log.status},"同步完成" if log.status=="success" else "同步失败，可重试")

    app.mount("/assets",StaticFiles(directory=ROOT/"static"),name="assets")
    @app.get("/",include_in_schema=False)
    def home():return FileResponse(ROOT/"static"/"index.html")
