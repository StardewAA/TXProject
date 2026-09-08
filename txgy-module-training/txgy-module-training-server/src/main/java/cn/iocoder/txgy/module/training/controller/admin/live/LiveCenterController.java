package cn.iocoder.txgy.module.training.controller.admin.live;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.admin.live.vo.LiveCenterRequests.*;
import cn.iocoder.txgy.module.training.service.live.LiveCenterService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/training/live-center")
@PreAuthorize("isAuthenticated()")
public class LiveCenterController {
 @Resource private LiveCenterService service;
 @GetMapping("/context") public CommonResult<?> context(){return success(service.context());}
 @GetMapping("/rooms") public CommonResult<?> rooms(@RequestParam(defaultValue="mine") String scope,@RequestParam(required=false) String keyword,@RequestParam(required=false) String from,@RequestParam(required=false) String to){return success(service.rooms(scope,keyword,from,to));}
 @PostMapping("/room/save") public CommonResult<?> save(@Valid @RequestBody Room req){return success(service.saveRoom(req));}
 @PostMapping("/room/review") public CommonResult<?> review(@Valid @RequestBody Review req){service.reviewRoom(req,false);return success(true);}
 @PostMapping("/room/start") public CommonResult<?> start(@Valid @RequestBody Id req){service.start(req.id());return success(true);}
 @PostMapping("/room/end") public CommonResult<?> end(@Valid @RequestBody Id req){service.end(req.id());return success(true);}
 @PostMapping("/room/book") public CommonResult<?> book(@Valid @RequestBody Id req){service.book(req.id());return success(true);}
 @PostMapping("/room/cancel-booking") public CommonResult<?> cancel(@Valid @RequestBody Id req){service.cancelBooking(req.id());return success(true);}
 @GetMapping("/playback") public CommonResult<?> playback(@RequestParam long roomId,@RequestParam(defaultValue="false") boolean replay){return success(service.playbackInfo(roomId,replay));}
 @GetMapping("/stream/publish") public CommonResult<?> publish(@RequestParam long roomId){return success(service.publish(roomId));}
 @GetMapping("/assistant-candidates") public CommonResult<?> candidates(@RequestParam long roomId){return success(service.assistantCandidates(roomId));}
 @GetMapping("/assistants") public CommonResult<?> assistants(@RequestParam long roomId){return success(service.assistantIds(roomId));}
 @PostMapping("/replay/save") public CommonResult<?> replay(@Valid @RequestBody Replay req){service.replay(req);return success(true);}
 @PostMapping("/replay/toggle") public CommonResult<?> toggleReplay(@Valid @RequestBody Toggle req){service.replayEnabled(req);return success(true);}
 @PostMapping("/replay/review") public CommonResult<?> reviewReplay(@Valid @RequestBody Review req){service.reviewRoom(req,true);return success(true);}
 @GetMapping("/profiles") public CommonResult<?> profiles(){return success(service.profiles());}
 @PostMapping("/profile/apply") public CommonResult<?> apply(@Valid @RequestBody Profile req){service.apply(req);return success(true);}
 @PostMapping("/profile/update") public CommonResult<?> update(@Valid @RequestBody Profile req){service.updateProfile(req);return success(true);}
 @PostMapping("/profile/review") public CommonResult<?> reviewProfile(@Valid @RequestBody Review req){service.reviewProfile(req);return success(true);}
 @PostMapping("/profile/toggle") public CommonResult<?> toggleProfile(@Valid @RequestBody Toggle req){service.enableProfile(req);return success(true);}
 @GetMapping("/employees") public CommonResult<?> employees(@RequestParam(required=false) String keyword){return success(service.employees(keyword));}
 @PostMapping("/booking/batch") public CommonResult<?> batch(@Valid @RequestBody Users req){service.batchBook(req);return success(true);}
 @GetMapping("/records") public CommonResult<?> records(@RequestParam long roomId){return success(service.records(roomId));}
 @PostMapping("/remind") public CommonResult<?> remind(@Valid @RequestBody Users req){return success(service.remind(req));}
 @GetMapping("/assignments") public CommonResult<?> assignments(@RequestParam long roomId,@RequestParam(defaultValue="false") boolean managed){return success(service.assignments(roomId,managed));}
 @PostMapping("/assignment/save") public CommonResult<?> assignment(@Valid @RequestBody Assignment req){return success(service.assignment(req));}
 @PostMapping("/assignment/submit") public CommonResult<?> submit(@Valid @RequestBody Submission req){service.submit(req);return success(true);}
 @GetMapping("/submissions") public CommonResult<?> submissions(@RequestParam long assignmentId){return success(service.submissions(assignmentId));}
 @PostMapping("/submission/grade") public CommonResult<?> grade(@Valid @RequestBody Grade req){service.grade(req);return success(true);}
 @GetMapping("/notices") public CommonResult<?> notices(){return success(service.notices());}
 @PostMapping("/notice/read") public CommonResult<?> read(@Valid @RequestBody Id req){service.readNotice(req.id());return success(true);}
 @GetMapping("/participants") public CommonResult<?> participants(@RequestParam long roomId){return success(service.participants(roomId));}
 @GetMapping("/moderation") public CommonResult<?> moderation(@RequestParam long roomId){return success(service.moderationLog(roomId));}
 @PostMapping("/moderation") public CommonResult<?> moderate(@Valid @RequestBody Moderation req){service.moderate(req);return success(true);}
 @PostMapping("/assistants") public CommonResult<?> assistants(@Valid @RequestBody Users req){service.assistants(req);return success(true);}
 @GetMapping("/quizzes") public CommonResult<?> quizzes(@RequestParam long roomId,@RequestParam(defaultValue="false") boolean managed){return success(service.quizzes(roomId,managed));}
 @PostMapping("/quiz/save") public CommonResult<?> quiz(@Valid @RequestBody Quiz req){return success(service.quiz(req));}
 @PostMapping("/quiz/answer") public CommonResult<?> answer(@Valid @RequestBody Answer req){service.answer(req);return success(true);}
 @PostMapping("/config") public CommonResult<?> config(@Valid @RequestBody Config req){service.configure(req);return success(true);}
 @GetMapping("/stats") public CommonResult<?> stats(@RequestParam(defaultValue="mine") String scope,@RequestParam(required=false) String from,@RequestParam(required=false) String to){return success(service.stats(scope,from,to));}
}
