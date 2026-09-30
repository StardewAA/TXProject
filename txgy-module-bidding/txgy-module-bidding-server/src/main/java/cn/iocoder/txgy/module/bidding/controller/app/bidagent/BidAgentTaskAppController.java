package cn.iocoder.txgy.module.bidding.controller.app.bidagent;

// [规范依据]《招投标模块开发规范》§4.2（controller 分层 admin/app）、§4.4
// [业务目标] 前台/门户「标书制作」页的一键生成入口：登录态 + 租户隔离 + 任务归属校验
// [为什么] 前台不感知执行底座（Node）与密钥；本模块是任务与配置的权威源

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentTaskVO;
import cn.iocoder.txgy.module.bidding.service.bidagent.BidAgentTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - 标书一键生成")
@RestController
@RequestMapping("/bidding/bid-agent/task")
@Validated
public class BidAgentTaskAppController {

    @Resource
    private BidAgentTaskService taskService;

    @PostMapping("/generate")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "一键生成标书（前台按钮入口）")
    public CommonResult<Long> generate(@Valid @RequestBody BidAgentTaskVO.GenerateReqVO reqVO) {
        return success(taskService.generate(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "生成任务详情（阶段/进度/待确认清单/产物）")
    public CommonResult<BidAgentTaskVO.RespVO> get(@RequestParam("id") Long id) {
        return success(taskService.get(id));
    }

    @GetMapping("/page")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "我的生成任务分页")
    public CommonResult<PageResult<BidAgentTaskVO.RespVO>> page(@Valid BidAgentTaskVO.PageReqVO reqVO) {
        return success(taskService.page(reqVO));
    }

    @PostMapping("/cancel")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "取消生成任务")
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        taskService.cancel(id);
        return success(true);
    }

    @PostMapping("/confirm")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "人工确认（HIL）：回填待确认问题的答复")
    public CommonResult<Boolean> confirm(@RequestParam("id") Long id,
                                         @Valid @RequestBody BidAgentTaskVO.ConfirmReqVO reqVO) {
        taskService.confirm(id, reqVO.getAnswers());
        return success(true);
    }

    @PostMapping("/retry")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "重试生成任务")
    public CommonResult<Boolean> retry(@RequestParam("id") Long id) {
        taskService.retry(id);
        return success(true);
    }

    @GetMapping("/outline")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "章节树与章节正文")
    public CommonResult<BidAgentTaskVO.OutlineRespVO> outline(@RequestParam("id") Long id) {
        return success(taskService.outline(id));
    }

    @GetMapping("/artifact")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "产物下载地址（docx）")
    public CommonResult<String> artifact(@RequestParam("id") Long id) {
        return success(taskService.artifactUrl(id));
    }

    @PostMapping("/delete")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "删除生成任务")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        taskService.delete(id);
        return success(true);
    }
}
