package cn.iocoder.txgy.module.training.service.live;

import cn.iocoder.txgy.module.training.dal.mysql.live.LiveCenterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static cn.iocoder.txgy.module.training.service.live.LiveCenterService.*;

/** Local SRS adapter: authenticated publish, protected HLS, automatic MP4 archive. */
@Service
public class LiveMediaService {
 @Resource private LiveCenterRepository db;
 private final ObjectMapper json = new ObjectMapper();
 @Value("${training.live.rtmp-url:rtmp://localhost:1935/live}") private String rtmpUrl;
 @Value("${training.live.srs-api:http://live-srs:1985}") private String srsApi;
 @Value("${training.live.hook-secret:}") private String hookSecret;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 public Map<String,Object> prepare(long roomId) {
  db.update("INSERT IGNORE INTO training_live_stream(room_id,publish_token) VALUES(?,?)",roomId,UUID.randomUUID().toString());
  db.update("UPDATE training_live_room SET stream_url=? WHERE id=? AND (stream_url IS NULL OR stream_url='')","/live-media/live/room-"+roomId+".m3u8",roomId);
  var row=db.one("SELECT publish_token FROM training_live_stream WHERE room_id=?",roomId);
  return Map.of("server",rtmpUrl,"streamKey","room-"+roomId+"?token="+row.get("publishToken"));
 }
 public String ticket(long roomId,long tenant,long user,boolean replay) {
  String token=UUID.randomUUID().toString();
  db.update("DELETE FROM training_live_media_ticket WHERE expires_at<NOW()");
  db.update("INSERT INTO training_live_media_ticket(token,room_id,tenant_id,user_id,replay,expires_at) VALUES(?,?,?,?,?,DATE_ADD(NOW(),INTERVAL 4 HOUR))",token,roomId,tenant,user,replay);
  return token;
 }
 public boolean authorize(String token,String path) {
  if(token==null||path==null)return false;
  var r=db.one("SELECT r.*,t.tenant_id viewer_tenant,t.user_id viewer_user,t.replay FROM training_live_media_ticket t JOIN training_live_room r ON r.id=t.room_id WHERE t.token=? AND t.expires_at>NOW() AND r.deleted=0",token);
  if(r==null||!"approved".equals(r.get("auditStatus")))return false;
  if(!"public".equals(r.get("visibility"))&&number(r,"tenantId")!=number(r,"viewerTenant"))return false;
  var kick=db.one("SELECT action FROM training_live_moderation WHERE room_id=? AND target_tenant_id=? AND user_id=? AND action IN ('kick','restore') ORDER BY id DESC LIMIT 1",number(r,"id"),number(r,"viewerTenant"),number(r,"viewerUser"));
  if(kick!=null&&"kick".equals(kick.get("action")))return false;
  String safePath=path.split("\\?",2)[0]; long id=number(r,"id");
  if(number(r,"replay")==1)return number(r,"status")==2&&number(r,"replayEnabled")==1&&"approved".equals(r.get("replayAuditStatus"))&&safePath.equals("/live-media/recordings/room-"+id+".mp4");
  return number(r,"status")==1&&safePath.matches("/live-media/live/room-"+id+"(?:\\.m3u8|-[0-9]+\\.ts)");
 }
 @Transactional
 public void hook(String secret,Map<String,Object> event) {
  check(!hookSecret.isBlank()&&java.security.MessageDigest.isEqual(hookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"无效媒体回调");
  String stream=Objects.toString(event.get("stream"),"");check(stream.matches("room-[0-9]+"),"无效直播标识");
  long id=Long.parseLong(stream.substring(5));String action=Objects.toString(event.get("action"),"");
  var r=db.one("SELECT r.*,s.publish_token FROM training_live_room r JOIN training_live_stream s ON s.room_id=r.id WHERE r.id=? AND r.deleted=0",id);check(r!=null,"直播不存在");
  if("on_publish".equals(action)) {
   String param=Objects.toString(event.get("param"),"");
   check(param.equals("?token="+r.get("publishToken"))&&"live".equals(event.get("app"))&&number(r,"status")==1&&"approved".equals(r.get("auditStatus")),"推流未获授权");
   db.update("UPDATE training_live_stream SET client_id=? WHERE room_id=?",Objects.toString(event.get("client_id"),""),id);
  } else if("on_unpublish".equals(action)) {
   db.update("UPDATE training_live_room SET status=2,actual_end_at=NOW() WHERE id=? AND status=1",id);
   db.update("INSERT IGNORE INTO training_live_replay_task(room_id,status,operator_id) VALUES(?,'awaiting_recording',?)",id,number(r,"lecturerUserId"));
  } else if("on_dvr".equals(action)) {
   String file=Objects.toString(event.get("file"),"");check(file.endsWith("/recordings/room-"+id+".mp4"),"回放路径不匹配");
   String url="/live-media/recordings/room-"+id+".mp4";
   db.update("INSERT INTO training_live_replay_task(room_id,status,recording_url,operator_id) VALUES(?,'ready',?,?) ON DUPLICATE KEY UPDATE status='ready',recording_url=VALUES(recording_url),issue=NULL",id,url,number(r,"lecturerUserId"));
   db.update("UPDATE training_live_room SET replay_video_url=?,replay_audit_status='pending' WHERE id=?",url,id);
  } else check(false,"不支持的回调事件");
 }
 public void disconnect(long id) {
  var row=db.one("SELECT client_id FROM training_live_stream WHERE room_id=?",id);
  if(row==null||row.get("clientId")==null)return;
  String client=row.get("clientId").toString();if(!client.matches("[a-zA-Z0-9_-]+"))return;
  http.sendAsync(HttpRequest.newBuilder(URI.create(srsApi+"/api/v1/clients/"+client)).timeout(Duration.ofSeconds(5)).DELETE().build(),HttpResponse.BodyHandlers.discarding());
 }
}
