package cn.iocoder.txgy.module.training.controller.portal.learner;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionEndRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionHeartbeatRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionStartReqVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionStartRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionVerifyReqVO;
import cn.iocoder.txgy.module.training.service.learner.LearnerSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "学员端 - 学员学习中心-学习会话（防挂机）")
@RestController
@RequestMapping("/training/portal/learner/session")
@Validated
public class PortalLearnerSessionController {

    @Resource
    private LearnerSessionService learnerSessionService;

    @GetMapping("/config")
    @Operation(summary = "获得学习会话参数（心跳间隔/超时/验证间隔等）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Map<String, Object>> getSessionConfig() {
        return success(learnerSessionService.getSessionConfig());
    }

    @PostMapping("/start")
    @Operation(summary = "开启学习会话（视频/文档/直播/回放）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<LearnerSessionStartRespVO> start(@Valid @RequestBody LearnerSessionStartReqVO reqVO) {
        return success(learnerSessionService.start(getLoginUserId(), reqVO));
    }

    @PostMapping("/heartbeat")
    @Operation(summary = "学习心跳（防挂机计时；文档会话可顺带上报当前页）")
    @Parameter(name = "sessionId", description = "学习会话 ID", required = true)
    @Parameter(name = "pageNo", description = "文档当前页（document 会话必传）")
    @Parameter(name = "totalPages", description = "文档总页数")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<LearnerSessionHeartbeatRespVO> heartbeat(
            @RequestParam("sessionId") Long sessionId,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "totalPages", required = false) Integer totalPages) {
        return success(learnerSessionService.heartbeat(getLoginUserId(), sessionId, pageNo, totalPages));
    }

    @PostMapping("/verify")
    @Operation(summary = "人机验证提交（答错 3 次会话判无效）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> verify(@Valid @RequestBody LearnerSessionVerifyReqVO reqVO) {
        learnerSessionService.verify(getLoginUserId(), reqVO);
        return success(true);
    }

    @PostMapping("/end")
    @Operation(summary = "结束学习会话并结算进度")
    @Parameter(name = "sessionId", description = "学习会话 ID", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<LearnerSessionEndRespVO> end(@RequestParam("sessionId") Long sessionId) {
        return success(learnerSessionService.end(getLoginUserId(), sessionId));
    }

    @PostMapping("/checkin")
    @Operation(summary = "学习打卡（有效学时 ≥ 15 秒可打一次）")
    @Parameter(name = "sessionId", description = "学习会话 ID", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<LearnerSessionEndRespVO> checkin(@RequestParam("sessionId") Long sessionId) {
        return success(learnerSessionService.checkin(getLoginUserId(), sessionId));
    }

}
