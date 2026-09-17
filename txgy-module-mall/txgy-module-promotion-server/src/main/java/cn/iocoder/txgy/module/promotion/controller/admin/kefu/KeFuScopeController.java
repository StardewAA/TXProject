package cn.iocoder.txgy.module.promotion.controller.admin.kefu;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeIdentity.require;
import static cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.*;

@RestController
@RequestMapping("/promotion/kefu-scope")
@RequiredArgsConstructor
public class KeFuScopeController {
    private final KeFuScopeService service;

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PostMapping("/agent")
    @PreAuthorize("@ss.hasPermission('promotion:kefu-agent:manage')")
    public CommonResult<Boolean> grant(@Valid @RequestBody GrantRequest request) {
        service.grant(require(2), request);
        return success(true);
    }

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @GetMapping("/agents")
    @PreAuthorize("@ss.hasPermission('promotion:kefu-agent:manage')")
    public CommonResult<List<Agent>> agents(@RequestParam("scope") String scope,
            @RequestParam("shopId") Long shop, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return success(service.agents(require(2), scope, shop, page, size));
    }

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @GetMapping("/workbench")
    @PreAuthorize("@ss.hasPermission('promotion:kefu-agent:receive')")
    public CommonResult<List<Session>> list(@RequestParam("scope") String scope,
            @RequestParam("shopId") Long shop, @RequestParam(defaultValue = "WAITING") String state,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return success(service.list(require(2), scope, shop, state, page, size));
    }

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PostMapping("/claim")
    @PreAuthorize("@ss.hasPermission('promotion:kefu-agent:receive')")
    public CommonResult<Session> claim(@Valid @RequestBody ChangeRequest request) {
        return success(service.claim(require(2), request.sessionId(), request.version()));
    }

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PostMapping("/close")
    @PreAuthorize("@ss.hasPermission('promotion:kefu-agent:receive')")
    public CommonResult<Session> close(@Valid @RequestBody ChangeRequest request) {
        return success(service.close(require(2), request.sessionId(), request.version()));
    }
}
