package cn.iocoder.txgy.module.training.controller.admin.live;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.training.service.live.LiveMediaService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/training/live-media")
public class LiveMediaController {
 @Resource private LiveMediaService media;
 @PermitAll @TenantIgnore @PostMapping("/hook")
 public Map<String,Integer> hook(@RequestParam String secret,@RequestBody Map<String,Object> event) {
  media.hook(secret,event);return Map.of("code",0);
 }
 @PermitAll @TenantIgnore @GetMapping("/authorize")
 public ResponseEntity<Void> authorize(@CookieValue(value="training_live_media",required=false) String token,
   @RequestHeader(value="X-Original-URI",required=false) String path) {
  return ResponseEntity.status(media.authorize(token,path)?204:403).build();
 }
}
