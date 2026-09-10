package cn.iocoder.txgy.module.training.controller.portal.learner;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.DanmakuSendReqVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionStartReqVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionStartRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LiveDanmakuRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LiveRoomGroupRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.PortalLiveRoomVO;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.enums.learner.LearnMediaTypeEnum;
import cn.iocoder.txgy.module.training.service.learner.LearnerLiveService;
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

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

@Tag(name = "学员端 - 学员学习中心-直播大厅/弹幕")
@RestController
@RequestMapping("/training/portal/learner/live")
@Validated
public class PortalLearnerLiveController {

    @Resource
    private LearnerLiveService learnerLiveService;
    @Resource
    private LearnerSessionService learnerSessionService;

    @GetMapping("/rooms")
    @Operation(summary = "获得直播大厅（未开始/直播中/已结束 分组）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<LiveRoomGroupRespVO> getRoomGroups() {
        return success(learnerLiveService.getRoomGroups());
    }

    @GetMapping("/room")
    @Operation(summary = "获得直播房间详情")
    @Parameter(name = "id", description = "房间 ID", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<PortalLiveRoomVO> getRoom(@RequestParam("id") Long id) {
        return success(learnerLiveService.getRoomDetail(id));
    }

    @PostMapping("/join")
    @Operation(summary = "进入直播间（直播中创建 live 观看会话，进入后按心跳计有效学时）")
    @Parameter(name = "id", description = "房间 ID", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<LearnerSessionStartRespVO> join(@RequestParam("id") Long id) {
        PortalLiveRoomVO room = learnerLiveService.getRoomDetail(id);
        if (room.getCanJoin() == null || !room.getCanJoin()) {
            throw exception(ErrorCodeConstants.LEARNER_LIVE_NOT_STARTED);
        }
        LearnerSessionStartReqVO reqVO = new LearnerSessionStartReqVO();
        reqVO.setMediaId(id);
        reqVO.setMediaType(LearnMediaTypeEnum.LIVE.getType());
        return success(learnerSessionService.start(getLoginUserId(), reqVO));
    }

    @PostMapping("/danmaku")
    @Operation(summary = "发送直播弹幕")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> sendDanmaku(@Valid @RequestBody DanmakuSendReqVO reqVO) {
        String nickname = getLoginUserNickname();
        if (nickname == null || nickname.isBlank()) {
            nickname = "学员" + getLoginUserId();
        }
        learnerLiveService.sendDanmaku(getLoginUserId(), nickname, reqVO.getRoomId(), reqVO.getContent());
        return success(true);
    }

    @GetMapping("/danmaku")
    @Operation(summary = "拉取房间弹幕（增量，afterId 为上次最后一条 id）")
    @Parameter(name = "roomId", description = "房间 ID", required = true)
    @Parameter(name = "afterId", description = "增量游标")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<LiveDanmakuRespVO>> getDanmaku(
            @RequestParam("roomId") Long roomId,
            @RequestParam(value = "afterId", required = false) Long afterId) {
        return success(learnerLiveService.getDanmakuList(roomId, afterId));
    }

}
