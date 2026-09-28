package cn.iocoder.txgy.module.bidding.controller.admin.bidagent;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentModelVO;
import cn.iocoder.txgy.module.bidding.service.bidagent.BidAgentModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 标书智能体云端模型")
@RestController
@RequestMapping("/bidding/bid-agent/model")
@Validated
public class BidAgentModelController {

    @Resource
    private BidAgentModelService modelService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:query')")
    @Operation(summary = "云端模型配置列表")
    public CommonResult<List<BidAgentModelVO.RespVO>> list() {
        return success(modelService.list());
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:query')")
    @Operation(summary = "云端模型配置详情（apiKey 脱敏）")
    public CommonResult<BidAgentModelVO.RespVO> get(@RequestParam("id") Long id) {
        return success(modelService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:update')")
    @Idempotent
    @Operation(summary = "保存云端模型配置（apiKey 留空=不修改）")
    public CommonResult<Long> save(@Valid @RequestBody BidAgentModelVO.SaveReqVO reqVO) {
        return success(modelService.save(reqVO));
    }

    @PostMapping("/test")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:update')")
    @Idempotent
    @Operation(summary = "云端模型连通性自检")
    public CommonResult<BidAgentModelVO.TestRespVO> test(@RequestBody BidAgentModelVO.TestReqVO reqVO) {
        return success(modelService.test(reqVO));
    }
}
