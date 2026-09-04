package cn.iocoder.txgy.module.training.controller.admin.learning;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LiveRoomPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LiveRoomRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LiveRoomSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LiveRoomDO;
import cn.iocoder.txgy.module.training.service.learning.LiveRoomService;
import cn.iocoder.txgy.module.training.service.learner.LearnerSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 学员学习中心-直播房间")
@RestController
@RequestMapping("/training/learning/live-room")
@Validated
public class LiveRoomController {

    @Resource
    private LiveRoomService liveRoomService;
    @Resource
    private LearnerSessionService learnerSessionService;

    @PostMapping("/create")
    @Operation(summary = "创建直播房间")
    @PreAuthorize("@ss.hasPermission('training:live-room:create')")
    public CommonResult<Long> createLiveRoom(@Valid @RequestBody LiveRoomSaveReqVO createReqVO) {
        return success(liveRoomService.createLiveRoom(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新直播房间")
    @PreAuthorize("@ss.hasPermission('training:live-room:update')")
    public CommonResult<Boolean> updateLiveRoom(@Valid @RequestBody LiveRoomSaveReqVO updateReqVO) {
        liveRoomService.updateLiveRoom(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除直播房间")
    @Parameter(name = "id", description = "房间编号", required = true)
    @PreAuthorize("@ss.hasPermission('training:live-room:delete')")
    public CommonResult<Boolean> deleteLiveRoom(@RequestParam("id") Long id) {
        liveRoomService.deleteLiveRoom(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得直播房间")
    @Parameter(name = "id", description = "房间编号", required = true)
    @PreAuthorize("@ss.hasPermission('training:live-room:query')")
    public CommonResult<LiveRoomRespVO> getLiveRoom(@RequestParam("id") Long id) {
        LiveRoomDO liveRoom = liveRoomService.getLiveRoom(id);
        return success(convert(liveRoom));
    }

    @GetMapping("/page")
    @Operation(summary = "获得直播房间分页")
    @PreAuthorize("@ss.hasPermission('training:live-room:query')")
    public CommonResult<PageResult<LiveRoomRespVO>> getLiveRoomPage(@Valid LiveRoomPageReqVO pageReqVO) {
        PageResult<LiveRoomDO> pageResult = liveRoomService.getLiveRoomPage(pageReqVO);
        List<LiveRoomRespVO> list = BeanUtils.toBean(pageResult.getList(), LiveRoomRespVO.class);
        list.forEach(vo -> vo.setViewerCount(
                (int) learnerSessionService.countActiveViewers(vo.getId())));
        return success(new PageResult<>(list, pageResult.getTotal()));
    }

    private LiveRoomRespVO convert(LiveRoomDO liveRoom) {
        if (liveRoom == null) {
            return null;
        }
        LiveRoomRespVO respVO = BeanUtils.toBean(liveRoom, LiveRoomRespVO.class);
        respVO.setViewerCount((int) learnerSessionService.countActiveViewers(liveRoom.getId()));
        return respVO;
    }

}
