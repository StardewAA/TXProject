package cn.iocoder.txgy.module.training.controller.portal.learner;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerTrackRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerTrackSummaryRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.CheckinRecordDO;
import cn.iocoder.txgy.module.training.service.learner.LearnerTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "学员端 - 学员学习中心-学习轨迹")
@RestController
@RequestMapping("/training/portal/learner/track")
@Validated
public class PortalLearnerTrackController {

    @Resource
    private LearnerTrackService learnerTrackService;

    @GetMapping("/list")
    @Operation(summary = "获得我的学习轨迹（分页）")
    @Parameter(name = "mediaType", description = "媒资类型过滤：video/document/live/replay")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<PageResult<LearnerTrackRespVO>> getMyTracks(PageParam pageParam,
                                                                    @RequestParam(value = "mediaType", required = false) String mediaType) {
        return success(learnerTrackService.getMyTracks(getLoginUserId(), mediaType, pageParam));
    }

    @GetMapping("/summary")
    @Operation(summary = "获得我的学习轨迹汇总（档案首页）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<LearnerTrackSummaryRespVO> getMySummary() {
        return success(learnerTrackService.getMySummary(getLoginUserId()));
    }

    @GetMapping("/checkins")
    @Operation(summary = "获得我的打卡记录（可选课程过滤）")
    @Parameter(name = "courseId", description = "课程 ID（可选）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<CheckinRecordDO>> getMyCheckins(
            @RequestParam(value = "courseId", required = false) Long courseId) {
        return success(learnerTrackService.getMyCheckins(getLoginUserId(), courseId));
    }

    @GetMapping("/vr-records")
    @Operation(summary = "获得 VR 实训记录（模块六 VR 联动预留）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<Object>> getVrRecords() {
        return success(List.of());
    }

}
