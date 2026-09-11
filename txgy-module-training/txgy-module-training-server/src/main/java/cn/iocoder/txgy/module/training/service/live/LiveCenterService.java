package cn.iocoder.txgy.module.training.service.live;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.training.controller.admin.live.vo.LiveCenterRequests.*;
import cn.iocoder.txgy.module.training.dal.mysql.live.LiveCenterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.net.URI;

/** 直播中心业务规则：所有写入均校验身份、租户、所有者与状态。 */
@Service
public class LiveCenterService {
 @Resource private LiveCenterRepository db;
 @Resource private LiveMediaService media;
 @Resource private org.springframework.cache.CacheManager caches;
 @Resource private SecurityFrameworkService ss;
 private final ObjectMapper json = new ObjectMapper();
 public void rejectLegacyMutation() {check(false,"请通过直播与讲师体系创建、编辑及审核直播");}
 public Map<String,Object> publish(long id) {teacher();manage(id);return media.prepare(id);}
 public Map<String,Object> playbackInfo(long id,boolean replay) {var r=playback(id,replay);var result=new LinkedHashMap<String,Object>();result.put("url",Objects.toString(r.get(replay?"replayVideoUrl":"streamUrl"),""));result.put("config",config());result.put("token",media.ticket(id,tenant(),user(),replay));return result;}
 public List<Map<String,Object>> assistantCandidates(long id) {var r=manage(id);return db.list("SELECT id,nickname FROM system_users WHERE tenant_id=? AND deleted=0 AND status=0 ORDER BY id LIMIT 500",number(r,"tenantId"));}
 public List<Long> assistantIds(long id) {manage(id);return db.list("SELECT user_id FROM training_live_assistant WHERE room_id=?",id).stream().map(r->number(r,"userId")).toList();}
 public long user() { Long id=SecurityFrameworkUtils.getLoginUserId();check(id!=null,"请先登录");return id; }
 public long tenant() { Long id=TenantContextHolder.getTenantId();check(id!=null,"缺少企业信息");return id; }
 private boolean adminIdentity() {return SecurityFrameworkUtils.getLoginUser()!=null && Objects.equals(SecurityFrameworkUtils.getLoginUser().getUserType(),2);}
 public boolean platform() {
  return adminIdentity() && db.count("SELECT COUNT(*) FROM system_user_role ur JOIN system_role r ON r.id=ur.role_id WHERE ur.user_id=? AND ur.tenant_id=? AND ur.deleted=0 AND r.deleted=0 AND r.status=0 AND r.code='super_admin'",user(),tenant())>0;
 }
 public boolean enterprise() {return adminIdentity() && (platform() || ss.hasPermission("training:live:enterprise"));}
 private void platformOnly() {check(platform(),"仅平台运营管理员可执行此操作");}
 private void enterpriseOnly() {check(enterprise(),"需要本企业直播管理权限");}
 public static void check(boolean ok,String message) {if(!ok)throw new ServiceException(1_045_060_001,message);}
 public static long number(Map<String,Object> row,String key) {Object v=row.get(key);return v instanceof Number n?n.longValue():v instanceof Boolean b?(b?1:0):v==null?0:Long.parseLong(v.toString());}
 private Map<String,Object> must(Map<String,Object> value,String message) {check(value!=null,message);return value;}
 private Map<String,Object> profile() {return db.one("SELECT * FROM training_live_profile WHERE tenant_id=? AND user_id=?",tenant(),user());}
 public void teacher() {var p=profile();check(platform() || (adminIdentity() && p!=null && "approved".equals(p.get("status")) && number(p,"enabled")==1),"请先完成讲师认证，且讲师账号须处于启用状态");}
 public void checkCourseCreation() {var p=profile();check(p==null || ("approved".equals(p.get("status")) && number(p,"enabled")==1),"讲师账号已停用，不能新建课程");}
 public Map<String,Object> context() {
  var result=new LinkedHashMap<String,Object>();result.put("platform",platform());result.put("enterprise",enterprise());
  var p=profile();result.put("profile",p);result.put("teacher",platform() || (adminIdentity() && p!=null && "approved".equals(p.get("status")) && number(p,"enabled")==1));
  result.put("userId",user());result.put("tenantId",tenant());result.put("config",config());return result;
 }
 public Map<String,Object> config() {return db.one("SELECT danmaku_enabled,mic_enabled FROM training_live_platform_config WHERE id=1");}
 @Transactional public void configure(Config req) {platformOnly();db.update("UPDATE training_live_platform_config SET danmaku_enabled=?,mic_enabled=?,updater_id=? WHERE id=1",req.danmakuEnabled(),req.micEnabled(),user());audit(null,null,"config","更新全局直播规则");}
 public Map<String,Object> room(long id) {return must(db.one("SELECT * FROM training_live_room WHERE id=? AND deleted=0",id),"直播不存在");}
 public Map<String,Object> manage(long id) {var r=room(id);check(platform() || (number(r,"tenantId")==tenant() && number(r,"lecturerUserId")==user()),"只能管理本人创建的直播");return r;}
 private Map<String,Object> control(long id) {var r=room(id);check(platform() || (number(r,"tenantId")==tenant() && (number(r,"lecturerUserId")==user() || db.count("SELECT COUNT(*) FROM training_live_assistant WHERE room_id=? AND tenant_id=? AND user_id=?",id,tenant(),user())>0)),"没有该直播的场控权限");return r;}
 public Map<String,Object> visible(long id) {
  var r=room(id);check("approved".equals(r.get("auditStatus")),"直播内容尚未通过审核或已被下架");
  check("public".equals(r.get("visibility")) || number(r,"tenantId")==tenant(),"该直播仅对所属企业开放");
  check(!moderated(id,"kick","restore"),"你已被移出该直播间");return r;
 }
 public Map<String,Object> playback(long id,boolean replay) {
  var r=visible(id);
  if(replay)check(number(r,"status")==2 && number(r,"replayEnabled")==1 && "approved".equals(r.get("replayAuditStatus")) && r.get("replayVideoUrl")!=null && !r.get("replayVideoUrl").toString().isBlank(),"回放尚未就绪、未通过审核或已停用");
  else check(number(r,"status")==1,"直播尚未开始或已经结束");
  return r;
 }
 public void danmakuAllowed(long id) {playback(id,false);check(number(config(),"danmakuEnabled")==1,"平台已关闭弹幕");check(!moderated(id,"mute","unmute"),"你已被禁言");}
 private boolean moderated(long roomId,String action,String reverse) {var last=db.one("SELECT action FROM training_live_moderation WHERE room_id=? AND target_tenant_id=? AND user_id=? AND action IN (?,?) ORDER BY id DESC LIMIT 1",roomId,tenant(),user(),action,reverse);return last!=null && action.equals(last.get("action"));}
 public List<Map<String,Object>> rooms(String scope,String keyword,String from,String to) {
  var args=new ArrayList<Object>();String where=" WHERE r.deleted=0";
  check(Set.of("browse","platform","enterprise","mine","control").contains(scope),"查询范围无效");
  if("browse".equals(scope)) {where+=" AND r.audit_status='approved' AND (r.visibility='public' OR r.tenant_id=?)";args.add(tenant());}
  else if("platform".equals(scope)){platformOnly();}
  else if("enterprise".equals(scope)){enterpriseOnly();where+=" AND (r.visibility='public' OR r.tenant_id=?) AND r.audit_status='approved'";args.add(tenant());}
  else if("control".equals(scope)) {where+=" AND r.tenant_id=? AND (r.lecturer_user_id=? OR EXISTS (SELECT 1 FROM training_live_assistant a WHERE a.room_id=r.id AND a.tenant_id=? AND a.user_id=?))";Collections.addAll(args,tenant(),user(),tenant(),user());}
  else {teacher();where+=" AND r.tenant_id=? AND r.lecturer_user_id=?";Collections.addAll(args,tenant(),user());}
  if(keyword!=null && !keyword.isBlank()){where+=" AND r.title LIKE ?";args.add("%"+keyword.trim()+"%");}
  if(from!=null && !from.isBlank()){where+=" AND r.plan_start_at>=?";args.add(from);}
  if(to!=null && !to.isBlank()){where+=" AND r.plan_start_at<?";args.add(to);}
  var rows=db.list("SELECT r.*,p.bio lecturer_bio,p.expertise lecturer_expertise,t.name organization FROM training_live_room r LEFT JOIN training_live_profile p ON p.tenant_id=r.tenant_id AND p.user_id=r.lecturer_user_id LEFT JOIN system_tenant t ON t.id=r.tenant_id"+where+" ORDER BY r.plan_start_at DESC LIMIT 500",args.toArray());
  for(var r:rows){long id=number(r,"id");r.put("booked",db.count("SELECT COUNT(*) FROM training_live_booking WHERE room_id=? AND tenant_id=? AND user_id=?",id,tenant(),user())>0);
   r.remove("publishToken");r.put("bookingCount",db.count("SELECT COUNT(*) FROM training_live_booking WHERE room_id=?",id));r.put("canManage",platform() || (number(r,"tenantId")==tenant() && number(r,"lecturerUserId")==user()));
   r.put("replayTask",db.one("SELECT status,issue FROM training_live_replay_task WHERE room_id=?",id));
   if("browse".equals(scope)||"enterprise".equals(scope)) {r.remove("streamUrl");r.remove("replayVideoUrl");}
  }return rows;
 }
 private static void url(String value) {if(value!=null&&value.matches("/live-media/(live/room-[0-9]+\\.m3u8|recordings/room-[0-9]+\\.mp4)"))return;if(value==null||value.isBlank())return;try{var uri=URI.create(value);check(Set.of("https","http").contains(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null,"请输入有效的 HTTP(S) 地址");}catch(IllegalArgumentException e){throw new ServiceException(1_045_060_001,"请输入有效的 HTTP(S) 地址");}}
 @Transactional public long saveRoom(Room req) {
  teacher();check(req.planEndAt().isAfter(req.planStartAt()),"结束时间须晚于开始时间（开始："+req.planStartAt()+"，结束："+req.planEndAt()+"）");check(req.planStartAt().isAfter(LocalDateTime.now()),"请选择未来的开播时间");url(req.coverUrl());url(req.streamUrl());
  var p=profile();String name=p==null?Optional.ofNullable(SecurityFrameworkUtils.getLoginUserNickname()).orElse("平台讲师"):p.get("name").toString();
  if(req.id()==null)return db.insert("INSERT INTO training_live_room(tenant_id,lecturer_user_id,title,remark,cover_url,lecturer_name,stream_url,plan_start_at,plan_end_at,credit_hours,visibility,status,audit_status,sort,creator,updater) VALUES(?,?,?,?,?,?,?,?,?,?,?,0,'pending',0,?,?)",tenant(),user(),req.title(),req.description(),req.coverUrl(),name,req.streamUrl(),req.planStartAt(),req.planEndAt(),req.creditHours(),req.visibility(),String.valueOf(user()),String.valueOf(user()));
  var r=manage(req.id());check(number(r,"status")==0,"仅未开播直播可以编辑");
  db.update("UPDATE training_live_room SET title=?,remark=?,cover_url=?,stream_url=?,plan_start_at=?,plan_end_at=?,credit_hours=?,visibility=?,audit_status='pending',audit_comment=NULL,update_time=NOW(),updater=? WHERE id=?",req.title(),req.description(),req.coverUrl(),req.streamUrl(),req.planStartAt(),req.planEndAt(),req.creditHours(),req.visibility(),String.valueOf(user()),req.id());return req.id();
 }
 private void audit(Long roomId,Long target,String action,String comment){db.insert("INSERT INTO training_live_audit_log(tenant_id,room_id,target_id,action,comment,operator_id) VALUES(?,?,?,?,?,?)",tenant(),roomId,target,action,comment,user());}
 private void notice(long t,long u,Long roomId,String kind,String content){db.insert("INSERT INTO training_live_notice(tenant_id,user_id,room_id,kind,content,operator_id) VALUES(?,?,?,?,?,?)",t,u,roomId,kind,content,user());}
 @Transactional public void reviewRoom(Review req,boolean replay) {platformOnly();var r=room(req.id());if(replay)check(number(r,"status")==2 && r.get("replayVideoUrl")!=null,"请先关联已生成的回放文件");check(!"rejected".equals(req.status()) || (req.comment()!=null&&!req.comment().isBlank()),"驳回时必须填写理由");
  String column=replay?"replay_audit_status":"audit_status";
  db.update("UPDATE training_live_room SET "+column+"=?,audit_comment=?,update_time=NOW() WHERE id=?",req.status(),req.comment(),req.id());audit(req.id(),null,replay?"replay_review":"room_review",req.comment());
  if(number(r,"lecturerUserId")>0)notice(number(r,"tenantId"),number(r,"lecturerUserId"),req.id(),"review","直播内容审核结果："+req.status()+"，"+Objects.toString(req.comment(),""));
 }
 @Transactional public void start(long id) {teacher();manage(id);media.prepare(id);var r=manage(id);check("approved".equals(r.get("auditStatus")),"直播预告须先通过审核");check(r.get("streamUrl")!=null&&!r.get("streamUrl").toString().isBlank(),"请先配置服务商提供的直播播放地址");check(db.update("UPDATE training_live_room SET status=1,actual_start_at=NOW() WHERE id=? AND status=0",id)==1,"直播已开播或已结束，请刷新");
  for(var b:db.list("SELECT tenant_id,user_id FROM training_live_booking WHERE room_id=?",id))notice(number(b,"tenantId"),number(b,"userId"),id,"start","你预约的直播「"+r.get("title")+"」已开播");audit(id,null,"start","开播并发送预约提醒");
 }
 @Transactional public void end(long id) {manage(id);check(db.update("UPDATE training_live_room SET status=2,actual_end_at=NOW() WHERE id=? AND status=1",id)==1,"仅直播中的房间可以结束");db.update("INSERT IGNORE INTO training_live_replay_task(room_id,status,operator_id) VALUES(?,'awaiting_recording',?)",id,user());audit(id,null,"end","创建回放归档任务，等待录制文件");media.disconnect(id);}
 @Transactional public void replay(Replay req) {control(req.roomId());var r=room(req.roomId());check(number(r,"status")==2,"直播结束后才能整理回放");url(req.url());check((req.url()!=null&&!req.url().isBlank()) || (req.issue()!=null&&!req.issue().isBlank()),"请填写回放地址或异常说明");boolean ready=req.url()!=null&&!req.url().isBlank();
  db.update("INSERT INTO training_live_replay_task(room_id,status,recording_url,issue,operator_id) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),recording_url=COALESCE(VALUES(recording_url),recording_url),issue=VALUES(issue),operator_id=VALUES(operator_id)",req.roomId(),ready?"ready":"failed",ready?req.url():null,req.issue(),user());
  if(ready)db.update("UPDATE training_live_room SET replay_video_url=?,replay_audit_status='pending' WHERE id=?",req.url(),req.roomId());audit(req.roomId(),null,"replay_archive",req.issue());
 }
 @Transactional public void replayEnabled(Toggle req) {manage(req.id());db.update("UPDATE training_live_room SET replay_enabled=? WHERE id=?",req.enabled(),req.id());audit(req.id(),null,req.enabled()?"replay_enable":"replay_disable","保留原始回放文件");}
 @Transactional public void book(long id) {var r=visible(id);check(number(r,"status")==0,"只能预约尚未开播的直播");db.update("INSERT IGNORE INTO training_live_booking(room_id,tenant_id,user_id,operator_id) VALUES(?,?,?,?)",id,tenant(),user(),user());}
 @Transactional public void cancelBooking(long id) {var r=room(id);check(number(r,"status")==0,"直播开播后不能取消预约");db.update("DELETE FROM training_live_booking WHERE room_id=? AND tenant_id=? AND user_id=?",id,tenant(),user());}
 private void employee(long id) {check(db.count("SELECT COUNT(*) FROM system_users WHERE id=? AND tenant_id=? AND deleted=0 AND status=0",id,tenant())==1,"只能选择本企业有效员工");}
 public List<Map<String,Object>> employees(String keyword) {enterpriseOnly();return db.list("SELECT id,nickname,dept_id FROM system_users WHERE tenant_id=? AND deleted=0 AND status=0 AND nickname LIKE ? ORDER BY id LIMIT 500",tenant(),"%"+Objects.toString(keyword,"")+"%");}
 @Transactional public void batchBook(Users req) {enterpriseOnly();var r=visible(req.roomId());check(number(r,"status")==0,"只能预约未开播直播");for(long id:new HashSet<>(req.userIds())){employee(id);db.update("INSERT IGNORE INTO training_live_booking(room_id,tenant_id,user_id,operator_id) VALUES(?,?,?,?)",req.roomId(),tenant(),id,user());}audit(req.roomId(),null,"batch_book","企业批量预约");}
 @Transactional public void apply(Profile req) {enterpriseOnly();employee(req.userId());var old=db.one("SELECT status FROM training_live_profile WHERE tenant_id=? AND user_id=?",tenant(),req.userId());check(old==null||"rejected".equals(old.get("status")),"该员工已有待审核或已认证的讲师资料");db.update("INSERT INTO training_live_profile(tenant_id,user_id,name,organization,expertise,bio,applicant_id) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),organization=VALUES(organization),expertise=VALUES(expertise),bio=VALUES(bio),applicant_id=VALUES(applicant_id),status='pending',review_comment=NULL",tenant(),req.userId(),req.name(),req.organization(),req.expertise(),req.bio(),user());}
 public List<Map<String,Object>> profiles() {if(platform())return db.list("SELECT * FROM training_live_profile ORDER BY create_time DESC LIMIT 500");enterpriseOnly();return db.list("SELECT * FROM training_live_profile WHERE tenant_id=? ORDER BY create_time DESC LIMIT 500",tenant());}
 @Transactional public void updateProfile(Profile req) {teacher();check(req.userId()==user(),"只能编辑本人讲师资料");check(db.update("UPDATE training_live_profile SET name=?,organization=?,expertise=?,bio=? WHERE tenant_id=? AND user_id=? AND status='approved'",req.name(),req.organization(),req.expertise(),req.bio(),tenant(),user())==1,"暂无认证讲师资料");db.update("UPDATE training_live_room SET lecturer_name=? WHERE tenant_id=? AND lecturer_user_id=?",req.name(),tenant(),user());}
 @Transactional public void reviewProfile(Review req) {platformOnly();var p=must(db.one("SELECT * FROM training_live_profile WHERE id=? FOR UPDATE",req.id()),"申请不存在");check("pending".equals(p.get("status")),"该申请已经审核，请刷新");check(!"rejected".equals(req.status())||(req.comment()!=null&&!req.comment().isBlank()),"请填写驳回理由");db.update("UPDATE training_live_profile SET status=?,review_comment=?,reviewer_id=?,reviewed_at=NOW() WHERE id=?",req.status(),req.comment(),user(),req.id());
  if("approved".equals(req.status())) {
   long t=number(p,"tenantId");var role=db.one("SELECT id FROM system_role WHERE tenant_id=? AND code='training_lecturer' AND deleted=0",t);
   long roleId=role==null?db.insert("INSERT INTO system_role(name,code,sort,data_scope,status,type,tenant_id,creator,updater) VALUES('培训讲师','training_lecturer',10,5,0,2,?,'module6','module6')",t):number(role,"id");
   if(db.count("SELECT COUNT(*) FROM system_user_role WHERE user_id=? AND role_id=? AND deleted=0",number(p,"userId"),roleId)==0)db.insert("INSERT INTO system_user_role(user_id,role_id,tenant_id,creator,updater) VALUES(?,?,?,'module6','module6')",number(p,"userId"),roleId,t);
   for(var menu:db.list("SELECT id FROM system_menu WHERE id IN (41000,41007) AND deleted=0")) if(db.count("SELECT COUNT(*) FROM system_role_menu WHERE role_id=? AND menu_id=? AND deleted=0",roleId,number(menu,"id"))==0) db.insert("INSERT INTO system_role_menu(role_id,menu_id,tenant_id,creator,updater) VALUES(?,?,?,'module6','module6')",roleId,number(menu,"id"),t);
  }
  if("approved".equals(req.status())) {
   long targetTenant=number(p,"tenantId"), targetUser=number(p,"userId");
   org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
    @Override public void afterCommit() {
     cn.iocoder.txgy.framework.tenant.core.util.TenantUtils.execute(targetTenant, () -> {
      var users=caches.getCache("user_role_ids");if(users!=null)users.evict(targetUser);
      var menus=caches.getCache("menu_role_ids");if(menus!=null){menus.evict(41000L);menus.evict(41007L);}
     });
    }
   });
  }
  notice(number(p,"tenantId"),number(p,"applicantId"),null,"lecturer_review","讲师「"+p.get("name")+"」入驻审核："+req.status()+"，"+Objects.toString(req.comment(),""));audit(null,req.id(),"lecturer_review",req.comment());
 }
 @Transactional public void enableProfile(Toggle req){platformOnly();check(db.update("UPDATE training_live_profile SET enabled=? WHERE id=? AND status='approved'",req.enabled(),req.id())==1,"只可启停已认证讲师");audit(null,req.id(),req.enabled()?"lecturer_enable":"lecturer_disable","讲师账号状态调整");}
 public List<Map<String,Object>> assignments(long id,boolean manage) {if(manage)manage(id);else visible(id);return db.list("SELECT a.*,s.id submission_id,s.content submission_content,s.score,s.feedback,s.reviewed_at FROM training_live_assignment a LEFT JOIN training_live_submission s ON s.assignment_id=a.id AND s.tenant_id=? AND s.user_id=? WHERE a.room_id=? ORDER BY a.id DESC",tenant(),user(),id);}
 @Transactional public long assignment(Assignment req){manage(req.roomId());check(req.dueAt().isAfter(LocalDateTime.now()),"截止时间须晚于当前时间");return db.insert("INSERT INTO training_live_assignment(room_id,tenant_id,title,content,due_at,creator_id) VALUES(?,?,?,?,?,?)",req.roomId(),tenant(),req.title(),req.content(),req.dueAt(),user());}
 @Transactional public void submit(Submission req){var a=must(db.one("SELECT * FROM training_live_assignment WHERE id=? FOR UPDATE",req.assignmentId()),"作业不存在");visible(number(a,"roomId"));check(((LocalDateTime)a.get("dueAt")).isAfter(LocalDateTime.now()),"作业已截止");var old=db.one("SELECT reviewed_at FROM training_live_submission WHERE assignment_id=? AND tenant_id=? AND user_id=?",req.assignmentId(),tenant(),user());check(old==null||old.get("reviewedAt")==null,"已批阅作业不能修改");db.update("INSERT INTO training_live_submission(assignment_id,tenant_id,user_id,content) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE content=VALUES(content)",req.assignmentId(),tenant(),user(),req.content());}
 public List<Map<String,Object>> submissions(long assignmentId){var a=must(db.one("SELECT room_id FROM training_live_assignment WHERE id=?",assignmentId),"作业不存在");manage(number(a,"roomId"));return db.list("SELECT s.*,u.nickname FROM training_live_submission s LEFT JOIN system_users u ON u.id=s.user_id AND u.tenant_id=s.tenant_id WHERE s.assignment_id=? ORDER BY s.create_time DESC",assignmentId);}
 @Transactional public void grade(Grade req){var s=must(db.one("SELECT s.*,a.room_id FROM training_live_submission s JOIN training_live_assignment a ON a.id=s.assignment_id WHERE s.id=? FOR UPDATE",req.id()),"提交记录不存在");manage(number(s,"roomId"));db.update("UPDATE training_live_submission SET score=?,feedback=?,reviewer_id=?,reviewed_at=NOW() WHERE id=?",req.score(),req.feedback(),user(),req.id());notice(number(s,"tenantId"),number(s,"userId"),number(s,"roomId"),"grade","你的直播课后作业已批阅，请查看结果");audit(number(s,"roomId"),req.id(),"grade","作业批阅");}
 public List<Map<String,Object>> notices(){return db.list("SELECT * FROM training_live_notice WHERE tenant_id=? AND user_id=? ORDER BY id DESC LIMIT 100",tenant(),user());}
 public void readNotice(long id){db.update("UPDATE training_live_notice SET read_at=NOW() WHERE id=? AND tenant_id=? AND user_id=?",id,tenant(),user());}
 @Transactional public void moderate(Moderation req){control(req.roomId());check(number(room(req.roomId()),"status")==1,"仅直播中可执行场控");check(db.count("SELECT COUNT(*) FROM training_learner_session WHERE media_type='live' AND media_id=? AND tenant_id=? AND user_id=?",req.roomId(),req.tenantId(),req.userId())>0 || db.count("SELECT COUNT(*) FROM training_live_booking WHERE room_id=? AND tenant_id=? AND user_id=?",req.roomId(),req.tenantId(),req.userId())>0,"该用户不是本场直播学员");db.insert("INSERT INTO training_live_moderation(room_id,target_tenant_id,user_id,action,reason,operator_id) VALUES(?,?,?,?,?,?)",req.roomId(),req.tenantId(),req.userId(),req.action(),req.reason(),user());}
 public List<Map<String,Object>> moderationLog(long id){control(id);return db.list("SELECT * FROM training_live_moderation WHERE room_id=? ORDER BY id DESC LIMIT 200",id);}
 @Transactional public void assistants(Users req){manage(req.roomId());check(number(room(req.roomId()),"tenantId")==tenant(),"请在直播所属企业配置场控");for(long id:req.userIds())employee(id);db.update("DELETE FROM training_live_assistant WHERE room_id=?",req.roomId());for(long id:new HashSet<>(req.userIds()))db.insert("INSERT INTO training_live_assistant(room_id,tenant_id,user_id) VALUES(?,?,?)",req.roomId(),tenant(),id);}
 @Transactional public long quiz(Quiz req){control(req.roomId());check(number(room(req.roomId()),"status")==1,"直播中才能发布测验");check(req.correctAnswer()<req.options().size(),"正确选项无效");try{return db.insert("INSERT INTO training_live_quiz(room_id,tenant_id,question,options_json,correct_answer,creator_id) VALUES(?,?,?,?,?,?)",req.roomId(),tenant(),req.question(),json.writeValueAsString(req.options()),req.correctAnswer(),user());}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new ServiceException(1_045_060_001,"测验选项格式错误");}}
 public List<Map<String,Object>> quizzes(long id,boolean managed){if(managed)control(id);else visible(id);var list=db.list("SELECT q.*,a.answer my_answer,a.correct my_correct FROM training_live_quiz q LEFT JOIN training_live_answer a ON a.quiz_id=q.id AND a.tenant_id=? AND a.user_id=? WHERE q.room_id=? ORDER BY q.id DESC",tenant(),user(),id);for(var q:list){if(!managed)q.remove("correctAnswer");else{q.put("answerCount",db.count("SELECT COUNT(*) FROM training_live_answer WHERE quiz_id=?",number(q,"id")));q.put("correctCount",db.count("SELECT COUNT(*) FROM training_live_answer WHERE quiz_id=? AND correct=1",number(q,"id")));}}return list;}
 @Transactional public void answer(Answer req){var q=must(db.one("SELECT * FROM training_live_quiz WHERE id=?",req.quizId()),"测验不存在");playback(number(q,"roomId"),false);check(db.count("SELECT COUNT(*) FROM training_live_answer WHERE quiz_id=? AND tenant_id=? AND user_id=?",req.quizId(),tenant(),user())==0,"已经提交本题答案");check(req.answer()<readOptions(q).size(),"选项无效");try{db.insert("INSERT INTO training_live_answer(quiz_id,tenant_id,user_id,answer,correct) VALUES(?,?,?,?,?)",req.quizId(),tenant(),user(),req.answer(),req.answer()==number(q,"correctAnswer"));}catch(org.springframework.dao.DuplicateKeyException e){throw new ServiceException(1_045_060_001,"已经提交本题答案");}}
 private List<?> readOptions(Map<String,Object> q){try{return json.readValue(q.get("optionsJson").toString(),List.class);}catch(Exception e){throw new ServiceException(1_045_060_001,"测验选项不可用");}}
 public List<Map<String,Object>> records(long roomId){enterpriseOnly();var r=room(roomId);check("public".equals(r.get("visibility"))||number(r,"tenantId")==tenant(),"不能查看其他企业内部直播");return learnerRecords(roomId,tenant());}
 public List<Map<String,Object>> participants(long roomId){control(roomId);return learnerRecords(roomId,null);}
 private List<Map<String,Object>> learnerRecords(long roomId,Long onlyTenant){
  var args=new ArrayList<Object>();Collections.addAll(args,roomId,roomId,roomId,roomId,roomId);
  String filter="";if(onlyTenant!=null){filter=" WHERE people.tenant_id=?";args.add(onlyTenant);}
  var rows=db.list("SELECT people.tenant_id,people.user_id,u.nickname,COALESCE(v.live_seconds,0) live_seconds,COALESCE(v.replay_seconds,0) replay_seconds,COALESCE(h.submitted,0) submitted,COALESCE(h.graded,0) graded,(SELECT COUNT(*) FROM training_live_assignment WHERE room_id=?) assignment_count FROM (SELECT tenant_id,user_id FROM training_live_booking WHERE room_id=? UNION SELECT tenant_id,user_id FROM training_learner_session WHERE media_id=? AND media_type IN ('live','replay') AND deleted=0) people LEFT JOIN system_users u ON u.id=people.user_id AND u.tenant_id=people.tenant_id LEFT JOIN (SELECT tenant_id,user_id,SUM(CASE WHEN media_type='live' THEN valid_seconds ELSE 0 END) live_seconds,SUM(CASE WHEN media_type='replay' THEN valid_seconds ELSE 0 END) replay_seconds FROM training_learner_session WHERE media_id=? AND media_type IN ('live','replay') AND status<>'invalid' AND deleted=0 GROUP BY tenant_id,user_id) v ON v.tenant_id=people.tenant_id AND v.user_id=people.user_id LEFT JOIN (SELECT s.tenant_id,s.user_id,COUNT(*) submitted,SUM(s.reviewed_at IS NOT NULL) graded FROM training_live_submission s JOIN training_live_assignment a ON a.id=s.assignment_id WHERE a.room_id=? GROUP BY s.tenant_id,s.user_id) h ON h.tenant_id=people.tenant_id AND h.user_id=people.user_id"+filter+" ORDER BY people.user_id LIMIT 2000",args.toArray());
  var r=room(roomId);long required=Math.max(1,Math.round(Double.parseDouble(Objects.toString(r.get("creditHours"),"1"))*3600));
  for(var row:rows){row.put("requiredSeconds",required);row.put("completed",number(row,"liveSeconds")+number(row,"replaySeconds")>=required&&number(row,"submitted")>=number(row,"assignmentCount"));}return rows;
 }
 @Transactional public int remind(Users req){enterpriseOnly();var rows=records(req.roomId());int sent=0;for(long id:new HashSet<>(req.userIds())){employee(id);var row=rows.stream().filter(v->number(v,"userId")==id).findFirst().orElse(null);check(row!=null,"只能督促本场已报名或参与的员工");boolean incomplete=!Boolean.TRUE.equals(row.get("completed"));if(incomplete){notice(tenant(),id,req.roomId(),"reminder","请完成直播学习及课后作业「"+room(req.roomId()).get("title")+"」");sent++;}}audit(req.roomId(),null,"remind","督促人数："+sent);return sent;}
 public List<Map<String,Object>> stats(String scope,String from,String to){check(Set.of("mine","platform").contains(scope),"统计范围无效");var rows=rooms(scope,null,from,to);for(var r:rows){long id=number(r,"id");var v=db.one("SELECT COUNT(DISTINCT CASE WHEN valid_seconds>0 THEN CONCAT(tenant_id,':',user_id) END) viewer_count,COALESCE(SUM(valid_seconds),0) valid_seconds FROM training_learner_session WHERE media_id=? AND media_type IN ('live','replay') AND deleted=0 AND status<>'invalid'",id);long viewers=number(v,"viewerCount");r.put("viewerCount",viewers);r.put("averageSeconds",viewers==0?0:number(v,"validSeconds")/viewers);long quizCount=db.count("SELECT COUNT(*) FROM training_live_quiz WHERE room_id=?",id);long answers=db.count("SELECT COUNT(*) FROM training_live_answer a JOIN training_live_quiz q ON q.id=a.quiz_id WHERE q.room_id=?",id);long assignments=db.count("SELECT COUNT(*) FROM training_live_assignment WHERE room_id=?",id);long submissions=db.count("SELECT COUNT(*) FROM training_live_submission s JOIN training_live_assignment a ON a.id=s.assignment_id WHERE a.room_id=?",id);long people=Math.max(viewers,number(r,"bookingCount"));r.put("quizRate",viewers==0||quizCount==0?0:Math.min(100,Math.round(100.0*answers/(viewers*quizCount))));r.put("homeworkRate",people==0||assignments==0?0:Math.min(100,Math.round(100.0*submissions/(people*assignments))));r.remove("streamUrl");r.remove("replayVideoUrl");}return rows;}
}
