"""Platform integration and validated extensions for the examination engine."""
from __future__ import annotations
import asyncio
import base64
import io
import ipaddress
import re
import socket
from urllib.parse import urlparse
from contextlib import asynccontextmanager
from pydantic import BaseModel, Field
from fastapi import Request
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.utils import ImageReader
from reportlab.platypus import Paragraph


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

    # 兼容 /training-exam 前缀：管理端以 /training-exam/* 反代本服务（vite 代理或网关），
    # 代理剥前缀失败时（如 vite 重启后 rewrite 未生效）本中间件兜底剥前缀，页面/API/静态资源均可访问。
    @app.middleware("http")
    async def strip_training_exam_prefix(request, call_next):
        path = request.scope.get("path", "")
        if path == "/training-exam":
            request.scope["path"] = "/"
            request.scope["raw_path"] = b"/"
        elif path.startswith("/training-exam/"):
            stripped = path[len("/training-exam"):]
            request.scope["path"] = stripped
            raw = request.scope.get("raw_path")
            if raw and raw.decode("utf-8", "ignore").startswith("/training-exam/"):
                request.scope["raw_path"] = raw[len(b"/training-exam"):]
        return await call_next(request)

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

    @app.get("/api/course-categories")
    def course_categories(actor: Actor = Depends(current_user)):
        """课程分类「课级（第三级）」选项，供题库「关联课程」标签下拉使用。"""
        try:
            tree = platform_get("/training/course-category/list-tree", actor.token, actor.tenant_id) or []
        except HTTPException as error:
            if getattr(error, "status_code", None) == 403:
                tree = []
            else:
                raise
        result = []

        def walk(nodes, chain):
            for node in nodes or []:
                if not isinstance(node, dict):
                    continue
                name = str(node.get("name") or "").strip()
                path = [*chain, name] if name else list(chain)
                children = node.get("children") or []
                if children:
                    walk(children, path)
                elif len(path) >= 3 or int(node.get("level") or 0) >= 3:
                    result.append({"id": node.get("id"), "name": name, "code": node.get("code"), "path": " / ".join(path)})

        walk(tree, [])
        return ok(result)

    @app.get("/api/course-exams")
    def course_exams(actor: Actor = Depends(current_user)):
        """课程管理（模块二）配置的结课考试，供「考试安排」展示各门课程的结课考试。"""
        try:
            rows = platform_get("/training/course/exam-plan-list", actor.token, actor.tenant_id) or []
        except HTTPException as error:
            if getattr(error, "status_code", None) == 403:
                rows = []
            else:
                raise
        result = []
        for item in rows:
            if not isinstance(item, dict):
                continue
            result.append({
                "course_id": item.get("courseId"),
                "course_name": item.get("courseName"),
                "exam_id": item.get("examId"),
                "exam_start_time": item.get("examStartTime"),
                "exam_end_time": item.get("examEndTime"),
                "status": item.get("status"),
            })
        return ok(result)

    def platform_page_rows(path, actor, page_size=200, max_pages=5):
        """分页拉取平台列表数据（平台单页上限为 200 条，超过会返回 400）。"""
        rows = []
        for page_no in range(1, max_pages + 1):
            try:
                data = platform_get(f"{path}?pageNo={page_no}&pageSize={page_size}", actor.token, actor.tenant_id) or {}
            except HTTPException as error:
                if getattr(error, "status_code", None) == 403:
                    break
                raise
            page_rows = [x for x in (data.get("list") or []) if isinstance(x, dict)]
            rows.extend(page_rows)
            total = int(data.get("total") or 0)
            if len(page_rows) < page_size or (total and len(rows) >= total):
                break
        return rows

    @app.get("/api/courses")
    def course_options(actor: Actor = Depends(current_user)):
        """课程管理中已有的课程，供试卷「关联课程」下拉使用。"""
        result = []
        for item in platform_page_rows("/training/course/page", actor):
            name = str(item.get("title") or item.get("name") or "").strip()
            if not name: continue
            result.append({"id": item.get("id"), "name": name, "code": item.get("code")})
        return ok(result)

    @app.get("/api/work-types")
    def work_type_options(actor: Actor = Depends(current_user)):
        """工种选项：与资源中心「标签管理」中的工种一致。"""
        return ok(platform_tag_options(actor, "work_type"))

    @app.get("/api/job-certs")
    def job_cert_options(actor: Actor = Depends(current_user)):
        """考试通过发放的工种资格证选项：来自工种资格证表。"""
        result = []
        for item in platform_page_rows("/training/job-cert/page", actor):
            if not isinstance(item, dict): continue
            name = str(item.get("certName") or "").strip()
            if not name: continue
            result.append({"id": item.get("id"), "name": name, "job_name": item.get("jobName"), "job_code": item.get("jobCode"), "required": item.get("isRequired")})
        return ok(result)

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
        """编辑试题：同步更新资源中心（模块一）考试题库中的试题。"""
        payload=question_payload(body,actor);payload["id"]=qid
        platform_put("/training/question/update",actor.token,actor.tenant_id,payload)
        row=db.scalar(select(Question).where(Question.platform_id==qid))
        if row is not None:
            row.q_type=body.q_type;row.stem=body.stem
            row.options=json.dumps(body.options,ensure_ascii=False) if body.options else None
            row.answer=json.dumps(body.answer,ensure_ascii=False);row.analysis=body.analysis
            row.tags=json.dumps(body.tags,ensure_ascii=False);row.difficulty=body.difficulty
            db.commit()
        return ok(message="试题已更新到资源中心考试题库")

    @app.get("/api/papers/{pid}")
    def paper_detail(pid:int,db:Session=Depends(db_session),actor:Actor=Depends(require("admin","teacher","reviewer"))):
        p=db.get(Paper,pid)
        if not p:raise HTTPException(404,"试卷不存在")
        return ok({"id":p.id,"score_limit":PAPER_TOTAL_LIMIT,"course_id":p.course_id,"course_name":p.course_name,
                   "work_types":parse(p.work_types,[]),"cert_names":parse(p.cert_names,[]),
                   "questions":[{**question_view(db.get(Question,x.question_id),True),"score":x.score} for x in sorted(p.questions,key=lambda x:x.sort_no)]})

    @app.put("/api/papers/{pid}")
    def update_paper(pid:int,body:PaperIn,db:Session=Depends(db_session),actor:Actor=Depends(require("admin","teacher"))):
        p=db.get(Paper,pid)
        if not p or p.status!="draft":raise HTTPException(409,"仅草稿试卷可修改参数")
        data=body.model_dump()
        data["work_types"]=json.dumps(name_list(body.work_types),ensure_ascii=False)
        data["cert_names"]=json.dumps(name_list(body.cert_names),ensure_ascii=False)
        if not data.get("course_name") and body.course_id:
            data["course_name"]=platform_course_name(actor,body.course_id)
        for key,value in data.items():setattr(p,key,value)
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

    # ---------- 企业 HTML 证书模板渲染（课程结业 / 工种资格） ----------
    TPL_DIR = ROOT / "static" / "templates"

    def category_path_by_id(actor, cat_id):
        """从平台课程分类树中找 categoryId 的完整路径（一/二/三级）"""
        tree = platform_get("/training/course-category/list-tree", actor.token, actor.tenant_id) or []
        def walk(nodes, chain):
            for n in nodes:
                cur = chain + [n.get("name")]
                if n.get("id") == cat_id:
                    return " / ".join([c for c in cur if c])
                r = walk(n.get("children") or [], cur)
                if r: return r
            return None
        return walk(tree, []) or ""

    def fill_course_snapshot(actor, cert, db):
        """结业证书的课程快照 lazy 补齐（发证时无平台 token，首次渲染/下载时补）。"""
        if cert.course_category and cert.business_tag and cert.study_hours: return
        if not cert.course_id: return
        course = platform_get(f"/training/course/get?id={cert.course_id}", actor.token, actor.tenant_id) or {}
        if not cert.course_name: cert.course_name = course.get("title") or ""
        if not cert.business_tag:
            raw = course.get("bizTags")
            if raw:
                try:
                    tags = json.loads(raw)
                    cert.business_tag = "、".join([t for t in tags if t]) if isinstance(tags, list) else str(raw)
                except Exception:
                    cert.business_tag = str(raw)
        minutes = course.get("durationMinutes") or 0
        if not cert.study_hours and minutes: cert.study_hours = f"{minutes/60:g} 学时"
        if not cert.course_category:
            cat = course.get("categoryId")
            if cat: cert.course_category = category_path_by_id(actor, cat)
        db.commit()

    def render_cert_html(cert_type, d, verify_url):
        """用企业 HTML 模板渲染证书；返回 None 表示该类型无模板（回退）。"""
        file = {"graduate": "cert_course.html", "worktype": "cert_worktype.html"}.get(cert_type)
        if not file: return None
        html = (TPL_DIR / file).read_text(encoding="utf-8")
        rep = {
            "certNo": d["cert_no"], "qualCertNo": d["cert_no"],
            "userName": d["learner"],
            "courseName": d.get("course_name") or d["title"],
            "score": f"{d['score']:g}",
            "courseCategory": d.get("course_category") or "—",
            "businessTag": d.get("business_tag") or "—",
            "studyHours": d.get("study_hours") or "—",
            "issueDate": str(d["issued_at"])[:10],
            "workTypeName": d.get("work_type_name") or "—",
            "skillLevel": d.get("skill_level") or "—",
            "relateCourseList": d.get("relate_courses") or "—",
            "paperName": d.get("paper_name") or "—",
            "verifyUrl": verify_url,
        }
        for k, v in rep.items():
            html = html.replace("${%s}" % k, v)
        # 模板中的静态二维码占位图 → 动态生成 verifyUrl 二维码
        import qrcode as _qrcode
        qr = _qrcode.make(verify_url)
        qbuf = io.BytesIO(); qr.save(qbuf, format="PNG")
        qr_uri = "data:image/png;base64," + base64.b64encode(qbuf.getvalue()).decode()
        html = re.sub(r'src="data:image/png;base64,[^"]*" alt="核验二维码"', 'src="' + qr_uri + '" alt="核验二维码"', html)
        return html

    @app.get("/api/my/certs/{cid}/render", response_class=HTMLResponse)
    def render_cert(cid:int, request:Request, db:Session=Depends(db_session), actor:Actor=Depends(current_user)):
        c = db.get(Certificate, cid)
        if not c or (actor.role not in {"admin","teacher","reviewer","viewer"} and c.user_id != actor.id): raise HTTPException(404, "证书不存在")
        if c.cert_type in ("graduate", "competence"): fill_course_snapshot(actor, c, db)
        d = cert_dict(c, db)
        host = request.headers.get("host", "localhost")
        verify = f"http://{host}/training-exam/v/{c.cert_no}"
        html = render_cert_html(c.cert_type, d, verify)
        if html is None: raise HTTPException(404, "该证书类型暂无企业模板")
        return HTMLResponse(html)

    @app.get("/v/{cert_no}", response_class=HTMLResponse)
    def verify_page(cert_no:str):
        """扫码核验页（免登录）：遍历租户库查发证记录。"""
        found = None
        for dbfile in DATA.glob("tenant-*.db"):
            try:
                eng = create_engine(f"sqlite:///{dbfile.as_posix()}", connect_args={"check_same_thread": False})
                with Session(eng, expire_on_commit=False) as vdb:
                    row = vdb.execute(text("select cert_no,cert_type,title,score,issued_at,status,user_id,course_name,work_type_name from certificates where cert_no=:no"), {"no": cert_no}).mappings().first()
                    if row:
                        found = dict(row)
                        u = vdb.execute(text("select name from users where id=:i"), {"i": found["user_id"]}).first()
                        found["learner"] = u[0] if u else "—"
                        break
            except Exception:
                continue
        ok_html = ""
        if found and found.get("status") == "valid":
            typ = CERT_TYPE_NAMES.get(found["cert_type"], found["cert_type"])
            extra = ""
            if found.get("course_name"): extra += f"<p>课程/试卷：{found['course_name']}</p>"
            if found.get("work_type_name"): extra += f"<p>工种：{found['work_type_name']}　等级：{found.get('skill_level') or '—'}</p>"
            ok_html = f"<h1 style='color:#1a7a3c'>✅ 证书真实有效</h1><p>证书编号：{found['cert_no']}</p><p>证书类型：{typ}</p><p>持证人：{found['learner']}</p><p>成绩：{found['score']:g} 分</p><p>签发日期：{str(found['issued_at'])[:10]}</p>{extra}"
        else:
            ok_html = "<h1 style='color:#b02020'>❌ 证书不存在或已作废</h1>"
        return HTMLResponse(f"<!DOCTYPE html><html lang='zh-CN'><head><meta charset='utf-8'><title>证书核验</title><style>body{{font-family:'Microsoft YaHei';background:#2b2f36;display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0}}div.box{{background:#fffdf5;border:3px solid #c8a24a;border-radius:8px;padding:36px 48px;max-width:520px;line-height:1.9;color:#333}}p{{margin:4px 0}}</style></head><body><div class='box'>{ok_html}</div></body></html>")

    @app.get("/api/my/certs/{cid}")
    def cert_data(cid:int,db:Session=Depends(db_session),actor:Actor=Depends(current_user)):
        c=db.get(Certificate,cid)
        if not c or (actor.role not in {"admin","teacher","reviewer","viewer"} and c.user_id!=actor.id):raise HTTPException(404,"证书不存在")
        return ok(cert_dict(c,db))

    # Replace original demo responses; never report mock external certification as real.
    replaced={"/api/my/certs/{cid}/download","/api/admin/cert-issuers","/api/admin/cert-issuers/{iid}/test","/api/admin/cert-sync-logs"}
    app.router.routes[:]=[r for r in app.router.routes if getattr(r,"path",None) not in replaced]

    @app.get("/api/my/certs/{cid}/download")
    def download(cid:int, request:Request, db:Session=Depends(db_session), actor:Actor=Depends(current_user)):
        """企业版式 PDF：reportlab 按 HTML 模板视觉重绘（金框红框/水印/混排正文/meta框/二维码/电子签章）"""
        from reportlab.pdfbase.cidfonts import UnicodeCIDFont
        c=db.get(Certificate,cid)
        if not c or (actor.role not in {"admin","teacher","reviewer","viewer"} and c.user_id!=actor.id):raise HTTPException(404,"证书不存在")
        if c.cert_type in ("graduate","competence"): fill_course_snapshot(actor,c,db)
        d=cert_dict(c,db)
        host=request.headers.get("host","localhost")
        verify=f"http://{host}/training-exam/v/{c.cert_no}"
        w,h=landscape(A4);S=0.875  # 模板 960x680 → A4 横向缩放
        buf=io.BytesIO();pdf=canvas.Canvas(buf,pagesize=(w,h))
        pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"))
        G1,G2,RED=colors.HexColor("#c8a24a"),colors.HexColor("#b8892f"),colors.HexColor("#9c1c1c")
        # 底色（米金渐变简化为纯色）+ 三重框
        pdf.setFillColor(colors.HexColor("#fdf9f0"));pdf.rect(0,0,w,h,stroke=0,fill=1)
        pdf.setStrokeColor(G1);pdf.setLineWidth(3.5);pdf.rect(12*S,12*S,w-24*S,h-24*S)
        pdf.setLineWidth(0.8);pdf.rect(20*S,20*S,w-40*S,h-40*S)
        pdf.setStrokeColor(RED);pdf.setLineWidth(1.8);pdf.rect(25*S,25*S,w-50*S,h-50*S)
        # 四角饰（L 形金线 + 红圆点）
        for cx,cy,dx,dy in ((25*S,h-25*S,1,-1),(w-25*S,h-25*S,-1,-1),(25*S,25*S,1,1),(w-25*S,25*S,-1,1)):
            pdf.setStrokeColor(G2);pdf.setLineWidth(2)
            pdf.line(cx,cy,cx+62*S*dx,cy);pdf.line(cx,cy,cx,cy+62*S*dy)
            pdf.setFillColor(RED);pdf.circle(cx+5*S*dx,cy-5*S*dy,4*S,stroke=0,fill=1)
        # 水印"证"
        pdf.setFillColor(colors.Color(0.612,0.11,0.11,alpha=0.05));pdf.setFont("STSong-Light",190)
        pdf.drawCentredString(w/2,h/2-60,"证")
        # 顶部平台条
        pdf.setFillColor(G2);pdf.setFont("STSong-Light",17)
        pdf.drawCentredString(w/2,h-58*S,"天信之星 · 防腐保温智慧平台")
        pdf.setFont("STSong-Light",9.5);pdf.setFillColor(colors.HexColor("#8a7a4a"))
        pdf.drawCentredString(w/2,h-74*S,"云 培 训 学 院 · 考 试 与 认 证 中 心")
        pdf.setStrokeColor(colors.Color(0.737,0.537,0.29,alpha=0.6));pdf.setLineWidth(0.7)
        pdf.line(w/2-160,h-86*S,w/2+160,h-86*S)
        # 主标题 + 英文 + 证书编号
        head={"graduate":"培 训 结 业 证 书","worktype":"工 种 资 格 证 书"}.get(c.cert_type,"考试合格证书")
        en={"graduate":"CERTIFICATE OF TRAINING COMPLETION","worktype":"CERTIFICATE OF OCCUPATIONAL QUALIFICATION"}.get(c.cert_type,"")
        pdf.setFillColor(colors.HexColor("#b52626"));pdf.setFont("STSong-Light",34)
        pdf.drawCentredString(w/2,h-160*S,head)
        pdf.setFillColor(colors.HexColor("#b0954f"));pdf.setFont("STSong-Light",8.5)
        pdf.drawCentredString(w/2,h-176*S,"—  "+en+"  —")
        pdf.setStrokeColor(colors.Color(0.737,0.537,0.29,alpha=0.35));pdf.setLineWidth(0.7)
        pdf.roundRect(w/2-95*S,h-206*S,190*S,17*S,8*S,stroke=1,fill=0)
        pdf.setFillColor(colors.HexColor("#8a7a5a"));pdf.setFont("STSong-Light",9.5)
        pdf.drawCentredString(w/2,h-201*S,f"证书编号 NO. {d['cert_no']}")
        # 正文（Paragraph 混排：姓名/课程/成绩红色加粗）
        is_grad=c.cert_type in ("graduate","competence")
        if is_grad:
            body_html=(f"　　兹证明 <font color='#9c1c1c'><b>{d['learner']}</b></font> 学员，已完成"
                       f"《<font color='#9c1c1c'><b>{d.get('course_name') or d['title']}</b></font>》课程全部学习任务，并通过本课程结课考试，"
                       f"考试成绩 <font color='#9c1c1c'><b>{d['score']:g}</b></font> 分，达到课程培训考核要求，特发此证，以资证明。")
            meta_html=(f"<b>课程分类：</b><font color='#9c1c1c'>{d.get('course_category') or '—'}</font>　｜　"
                       f"<b>业务标签：</b><font color='#9c1c1c'>{d.get('business_tag') or '—'}</font><br/>"
                       f"<b>总培训学时：</b><font color='#9c1c1c'>{d.get('study_hours') or '—'}</font>　｜　"
                       f"<b>发证日期：</b><font color='#9c1c1c'>{str(d['issued_at'])[:10]}</font>")
        else:
            body_html=(f"　　持证人 <font color='#9c1c1c'><b>{d['learner']}</b></font>，对应工种 "
                       f"<font color='#9c1c1c'><b>{d.get('work_type_name') or '—'}</b></font>，技能成长等级 "
                       f"<font color='#9c1c1c'><b>{d.get('skill_level') or '—'}</b></font>。该学员已完成对应工种配套系列课程学习，"
                       f"通过工种资格认证综合考试，具备该工种对应岗位作业能力，特发本工种资格证书。")
            meta_html=(f"<b>关联课程：</b><font color='#9c1c1c'>{d.get('relate_courses') or '—'}</font><br/>"
                       f"<b>考核试卷：</b><font color='#9c1c1c'>{d.get('paper_name') or '—'}</font>　｜　"
                       f"<b>发证日期：</b><font color='#9c1c1c'>{str(d['issued_at'])[:10]}</font>")
        body_style=ParagraphStyle("body",fontName="STSong-Light",fontSize=14,leading=26,textColor=colors.HexColor("#2a2118"))
        meta_style=ParagraphStyle("meta",fontName="STSong-Light",fontSize=10.5,leading=18,textColor=colors.HexColor("#4a4030"))
        bp=Paragraph(body_html,body_style);bp.wrapOn(pdf,w-2*88*S,200*S);bp.drawOn(pdf,88*S,h-380*S)
        pdf.setFillColor(colors.Color(0.784,0.635,0.29,alpha=0.10));pdf.rect(88*S,118*S,w-176*S,92*S,stroke=0,fill=1)
        pdf.setFillColor(colors.Color(0.784,0.635,0.29,alpha=0.45));pdf.rect(88*S,118*S,w-176*S,92*S,stroke=1,fill=0)
        pdf.setFillColor(G1);pdf.rect(88*S,118*S,3*S,92*S,stroke=0,fill=1)
        mp=Paragraph(meta_html,meta_style);mp.wrapOn(pdf,w-2*88*S-24*S,80*S);mp.drawOn(pdf,88*S+14*S,126*S)
        # 二维码（verifyUrl）
        import qrcode
        qr_img=qrcode.make(verify);qbuf=io.BytesIO();qr_img.save(qbuf,format="PNG");qbuf.seek(0)
        pdf.drawImage(ImageReader(qbuf),80*S,42*S,width=52*S,height=52*S)
        pdf.setFillColor(colors.HexColor("#a99a88"));pdf.setFont("STSong-Light",7)
        pdf.drawCentredString(80*S+26*S,38*S,"扫码核验真伪")
        # 签章（base64 PNG，与 HTML 模板同一枚）
        import re as _re
        m=_re.search(r'class="seal" src="data:image/png;base64,([^"]+)"',render_cert_html(c.cert_type,d,verify) or "")
        if m:
            seal_img=ImageReader(io.BytesIO(base64.b64decode(m.group(1))))
            pdf.saveState();pdf.translate(w-110*S,66*S+66*S);pdf.rotate(-14)
            pdf.drawImage(seal_img,0,0,width=115*S,height=115*S,mask="auto");pdf.restoreState()
        # 页脚右下：机构 + 日期
        pdf.setFillColor(RED);pdf.setFont("STSong-Light",12)
        pdf.drawRightString(w-90*S,h-64*S,"天信管业科技集团有限公司")
        pdf.setFillColor(colors.HexColor("#4a4030"));pdf.setFont("STSong-Light",9)
        pdf.drawRightString(w-90*S,h-78*S,"云培训学院 考试与认证中心 颁发")
        pdf.drawRightString(w-90*S,h-92*S,f"发证日期：{str(d['issued_at'])[:10]}")
        # 底部备注
        pdf.setFillColor(colors.HexColor("#b3a88f"));pdf.setFont("STSong-Light",6.8)
        pdf.drawCentredString(w/2,26*S,f"本证书由天信之星防腐保温智慧平台·云培训学院考试与认证模块自动生成，可通过平台查验真伪。查验地址：{verify}")
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
