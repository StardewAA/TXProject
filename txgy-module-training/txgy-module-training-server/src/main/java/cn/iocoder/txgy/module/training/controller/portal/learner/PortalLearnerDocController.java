package cn.iocoder.txgy.module.training.controller.portal.learner;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.DocPageReqVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.DocStatusRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionHeartbeatRespVO;
import cn.iocoder.txgy.module.training.service.learner.LearnerSessionService;
import cn.iocoder.txgy.module.training.service.learner.LearnerTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "学员端 - 学员学习中心-文档学习")
@RestController
@RequestMapping("/training/portal/learner/doc")
@Validated
public class PortalLearnerDocController {

    @Resource
    private LearnerSessionService learnerSessionService;
    @Resource
    private LearnerTrackService learnerTrackService;

    @PostMapping("/page")
    @Operation(summary = "文档翻页/停留上报（内部走心跳计时，按有效停留累计）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<LearnerSessionHeartbeatRespVO> reportPage(@Valid @RequestBody DocPageReqVO reqVO) {
        return success(learnerSessionService.heartbeat(getLoginUserId(), reqVO.getSessionId(),
                reqVO.getPageNo(), reqVO.getTotalPages()));
    }

    @GetMapping("/status")
    @Operation(summary = "获得文档媒资学习状态（恢复续学/完成提示）")
    @Parameter(name = "courseId", description = "课程 ID", required = true)
    @Parameter(name = "mediaId", description = "文档媒资 ID", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<DocStatusRespVO> getDocStatus(
            @RequestParam("courseId") @NotNull(message = "课程不能为空") Long courseId,
            @RequestParam("mediaId") @NotNull(message = "媒资不能为空") Long mediaId) {
        return success(learnerTrackService.getDocStatus(getLoginUserId(), courseId, mediaId));
    }

}
