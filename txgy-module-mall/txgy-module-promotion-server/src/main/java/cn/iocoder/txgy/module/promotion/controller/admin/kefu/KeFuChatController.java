package cn.iocoder.txgy.module.promotion.controller.admin.kefu;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.module.promotion.service.kefu.chat.*;
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
public class KeFuChatController {
    private final KeFuChatService service;
    private final KeFuChatCapability capability;

    @ModelAttribute
    public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control","private, no-store"); response.setHeader("Pragma","no-cache"); }

    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/capability") @PermitAll
    public CommonResult<Capability> capability(){return success(capability.get());}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/agent/thread") @PreAuthorize("@ss.hasPermission('promotion:kefu-agent:receive')")
    public CommonResult<KeFuChatModels.Thread> thread(@RequestParam String sessionId){return success(service.thread(require(2),sessionId,true));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/agent/messages") @PreAuthorize("@ss.hasPermission('promotion:kefu-agent:receive')")
    public CommonResult<MessagePage> messages(@RequestParam String sessionId,@RequestParam(defaultValue="0") Long afterId,@RequestParam(defaultValue="50") int limit){
        return success(service.messages(require(2),sessionId,true,afterId,limit));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/agent/send") @PreAuthorize("@ss.hasPermission('promotion:kefu-agent:receive')")
    public CommonResult<SendResult> send(@Valid @RequestBody SendRequest request){return success(service.send(require(2),request,true));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/agent/read") @PreAuthorize("@ss.hasPermission('promotion:kefu-agent:receive')")
    public CommonResult<KeFuChatModels.Thread> read(@Valid @RequestBody ReadRequest request){return success(service.read(require(2),request,true));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/agent/heartbeat") @PreAuthorize("@ss.hasPermission('promotion:kefu-agent:receive')")
    public CommonResult<Heartbeat> heartbeat(@Valid @RequestBody HeartbeatRequest request){return success(service.heartbeat(require(2),request));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/agent/workbench") @PreAuthorize("@ss.hasPermission('promotion:kefu-agent:receive')")
    public CommonResult<List<KeFuChatModels.Thread>> workbench(@RequestParam String scope,@RequestParam Long shopId,@RequestParam(defaultValue="WAITING") String state,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return success(service.workbench(require(2),scope,shopId,state,page,size));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @GetMapping("/faq") @PreAuthorize("@ss.hasPermission('promotion:kefu-faq:manage')")
    public CommonResult<FaqConfig> faq(@RequestParam String scope,@RequestParam Long shopId){return success(service.faq(require(2),scope,shopId));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PutMapping("/faq") @PreAuthorize("@ss.hasPermission('promotion:kefu-faq:manage')")
    public CommonResult<FaqConfig> saveFaq(@Valid @RequestBody FaqSaveRequest request){return success(service.saveFaq(require(2),request));}
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PostMapping("/faq/publish") @PreAuthorize("@ss.hasPermission('promotion:kefu-faq:manage')")
    public CommonResult<FaqConfig> publishFaq(@Valid @RequestBody FaqPublishRequest request){return success(service.publishFaq(require(2),request));}
}
