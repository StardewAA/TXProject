package cn.iocoder.txgy.module.training.service.live;
import cn.iocoder.txgy.module.training.dal.mysql.live.LiveCenterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveMediaServiceTest {
 private LiveMediaService service(Map<String,Object> row,Map<String,Object> moderation) {
  LiveCenterRepository db=mock(LiveCenterRepository.class);
  when(db.one(anyString(),any(Object[].class))).thenAnswer(inv -> inv.getArgument(0,String.class).contains("training_live_media_ticket")?row:moderation);
  var service=new LiveMediaService();ReflectionTestUtils.setField(service,"db",db);return service;
 }
 private Map<String,Object> room() {var r=new HashMap<String,Object>();r.putAll(Map.of("id",7L,"auditStatus","approved","visibility","enterprise","tenantId",1L,"viewerTenant",1L,"viewerUser",8L,"replay",0,"status",1));return r;}
 @Test void permitsOnlyMatchingRoomHlsSegments(){var service=service(room(),null);assertTrue(service.authorize("ticket","/live-media/live/room-7.m3u8"));assertTrue(service.authorize("ticket","/live-media/live/room-7-3.ts"));assertFalse(service.authorize("ticket","/live-media/live/room-8.m3u8"));assertFalse(service.authorize("ticket","/live-media/live/room-7/../room-8.m3u8"));}
 @Test void deniesOtherEnterprise(){var r=room();r.put("viewerTenant",2L);assertFalse(service(r,null).authorize("ticket","/live-media/live/room-7.m3u8"));r.put("visibility","public");assertTrue(service(r,null).authorize("ticket","/live-media/live/room-7.m3u8"));}
 @Test void rejectedContentAndKickedLearnersLoseAccess(){var r=room();assertFalse(service(r,Map.of("action","kick")).authorize("ticket","/live-media/live/room-7.m3u8"));r.put("auditStatus","rejected");assertFalse(service(r,null).authorize("ticket","/live-media/live/room-7.m3u8"));}
 @Test void replayRequiresApprovalAndEnabledFlag(){var r=room();r.putAll(Map.of("replay",1,"status",2,"replayEnabled",1,"replayAuditStatus","approved"));var s=service(r,null);assertTrue(s.authorize("ticket","/live-media/recordings/room-7.mp4"));r.put("replayEnabled",0);assertFalse(s.authorize("ticket","/live-media/recordings/room-7.mp4"));r.put("replayEnabled",1);r.put("replayAuditStatus","pending");assertFalse(s.authorize("ticket","/live-media/recordings/room-7.mp4"));}
 @Test void missingTicketIsRejected(){var s=service(null,null);assertFalse(s.authorize("bad","/live-media/live/room-7.m3u8"));assertFalse(s.authorize(null,"/live-media/live/room-7.m3u8"));}
}
