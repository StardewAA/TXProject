package cn.iocoder.txgy.module.bidding.controller.admin.bidagent;

// [规范依据]《招投标模块开发规范》§4.2、§4.5
// [业务目标] 标书智能体·智能体管理：列表/详情/保存 + 试跑；
//           前台编排通过 GET /agent/get?taskType= 读取生效配置（五类智能体提示词均以后台为准）

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentPreviewVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentProfileVO;
import cn.iocoder.txgy.module.bidding.service.bidagent.BidAgentPreviewService;
import cn.iocoder.txgy.module.bidding.service.bidagent.BidAgentProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 标书智能体配置")
@RestController
@RequestMapping("/bidding/bid-agent")
@Validated
public class BidAgentConfigController {

    @Resource
    private BidAgentProfileService profileService;
    @Resource
    private BidAgentPreviewService previewService;

    @GetMapping("/agent/list")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:query')")
    @Operation(summary = "智能体列表（五类任务，含是否可编辑）")
    public CommonResult<List<BidAgentProfileVO.RespVO>> listAgent() {
        return success(profileService.list());
    }

    @GetMapping("/agent/get")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:query')")
    @Operation(summary = "智能体详情/按任务类型取生效配置（前台编排调用 taskType 参数）")
    public CommonResult<BidAgentProfileVO.RespVO> getAgent(@RequestParam(value = "id", required = false) Long id,
                                                          @RequestParam(value = "taskType", required = false) String taskType) {
        return success(id != null ? profileService.get(id) : profileService.getByTaskType(taskType));
    }

    @PostMapping("/agent/save")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:update')")
    @Idempotent
    @Operation(summary = "智能体配置保存（提示词以后台为唯一事实源；editable=0 的锁机制仍拒绝保存）")
    public CommonResult<Long> saveAgent(@Valid @RequestBody BidAgentProfileVO.SaveReqVO reqVO) {
        return success(profileService.save(reqVO));
    }

    @PostMapping("/preview")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:preview')")
    @Idempotent
    @Operation(summary = "智能体试跑（粘贴一段招标文件片段，用当前提示词即时验证；AI 未启用时显式报未配置）")
    public CommonResult<BidAgentPreviewVO.RespVO> preview(@Valid @RequestBody BidAgentPreviewVO.ReqVO reqVO) {
        return success(previewService.preview(reqVO));
    }
}
