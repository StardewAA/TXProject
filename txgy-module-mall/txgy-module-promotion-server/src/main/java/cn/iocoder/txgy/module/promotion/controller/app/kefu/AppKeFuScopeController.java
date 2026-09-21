package cn.iocoder.txgy.module.promotion.controller.app.kefu;

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

/** Versioned, opt-in scope foundation. Legacy /kefu-message is deliberately not reused. */
@RestController
@RequestMapping("/promotion/kefu-scope")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AppKeFuScopeController {
    private final KeFuScopeService service;

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PostMapping("/open")
    public CommonResult<Session> open(@Valid @RequestBody OpenRequest request) {
        return success(service.open(require(1), request.scope(), request.shopId()));
    }

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @GetMapping("/get")
    public CommonResult<Session> get(@RequestParam("id") String id) {
        return success(service.own(require(1), id));
    }

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @GetMapping("/workbench")
    public CommonResult<List<Session>> list(@RequestParam("scope") String scope,
            @RequestParam("shopId") Long shop, @RequestParam(defaultValue = "WAITING") String state,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return success(service.list(require(1), scope, shop, state, page, size));
    }

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PostMapping("/claim")
    public CommonResult<Session> claim(@Valid @RequestBody ChangeRequest request) {
        return success(service.claim(require(1), request.sessionId(), request.version()));
    }

    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PostMapping("/close")
    public CommonResult<Session> close(@Valid @RequestBody ChangeRequest request) {
        return success(service.close(require(1), request.sessionId(), request.version()));
    }
}
