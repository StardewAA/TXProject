package cn.iocoder.txgy.module.promotion.controller.app.kefu;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.module.promotion.service.kefu.chat.*;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.*;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeIdentity.require;
import static cn.iocoder.txgy.module.promotion.service.kefu.chat.KeFuChatModels.*;

@RestController
@RequestMapping("/promotion/kefu-chat")
@RequiredArgsConstructor
public class AppKeFuChatController {
    private final KeFuChatService service;
    private final KeFuChatCapability capability;

    @ModelAttribute
    public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control","private, no-store"); response.setHeader("Pragma","no-cache"); }

    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/capability") @PermitAll
    public CommonResult<Capability> capability(){return success(capability.get());}

    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/open") @PreAuthorize("isAuthenticated()")
    public CommonResult<KeFuChatModels.Thread> open(@Valid @RequestBody OpenRequest request){return success(service.open(require(1),request.scope(),request.shopId()));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/thread") @PreAuthorize("isAuthenticated()")
    public CommonResult<KeFuChatModels.Thread> thread(@RequestParam String sessionId){return success(service.thread(require(1),sessionId,false));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/messages") @PreAuthorize("isAuthenticated()")
    public CommonResult<MessagePage> messages(@RequestParam String sessionId,@RequestParam(defaultValue="0") Long afterId,@RequestParam(defaultValue="50") int limit){
        return success(service.messages(require(1),sessionId,false,afterId,limit));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/send") @PreAuthorize("isAuthenticated()")
    public CommonResult<SendResult> send(@Valid @RequestBody SendRequest request){return success(service.send(require(1),request,false));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/read") @PreAuthorize("isAuthenticated()")
    public CommonResult<KeFuChatModels.Thread> read(@Valid @RequestBody ReadRequest request){return success(service.read(require(1),request,false));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/human") @PreAuthorize("isAuthenticated()")
    public CommonResult<KeFuChatModels.Thread> human(@Valid @RequestBody ChangeRequest request){return success(service.human(require(1),request));}

    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/agent/thread") @PreAuthorize("isAuthenticated()")
    public CommonResult<KeFuChatModels.Thread> agentThread(@RequestParam String sessionId){return success(service.thread(require(1),sessionId,true));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/agent/messages") @PreAuthorize("isAuthenticated()")
    public CommonResult<MessagePage> agentMessages(@RequestParam String sessionId,@RequestParam(defaultValue="0") Long afterId,@RequestParam(defaultValue="50") int limit){
        return success(service.messages(require(1),sessionId,true,afterId,limit));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/agent/send") @PreAuthorize("isAuthenticated()")
    public CommonResult<SendResult> agentSend(@Valid @RequestBody SendRequest request){return success(service.send(require(1),request,true));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/agent/read") @PreAuthorize("isAuthenticated()")
    public CommonResult<KeFuChatModels.Thread> agentRead(@Valid @RequestBody ReadRequest request){return success(service.read(require(1),request,true));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/agent/heartbeat") @PreAuthorize("isAuthenticated()")
    public CommonResult<Heartbeat> heartbeat(@Valid @RequestBody HeartbeatRequest request){return success(service.heartbeat(require(1),request));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/agent/workbench") @PreAuthorize("isAuthenticated()")
    public CommonResult<List<KeFuChatModels.Thread>> workbench(@RequestParam String scope,@RequestParam Long shopId,@RequestParam(defaultValue="WAITING") String state,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return success(service.workbench(require(1),scope,shopId,state,page,size));}
}
