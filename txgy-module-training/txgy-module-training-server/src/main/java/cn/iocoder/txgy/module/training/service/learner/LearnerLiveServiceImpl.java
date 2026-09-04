package cn.iocoder.txgy.module.training.service.learner;

import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LiveDanmakuRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LiveRoomGroupRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.PortalLiveRoomVO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LiveDanmakuDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LiveRoomDO;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LiveDanmakuMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LiveRoomMapper;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.service.learning.LearningConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 学员端 - 直播/弹幕 Service 实现
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class LearnerLiveServiceImpl implements LearnerLiveService {

    /** 房间状态：未开始/直播中/已结束 */
    private static final int STATUS_UPCOMING = 0;
    private static final int STATUS_LIVING = 1;
    private static final int STATUS_ENDED = 2;

    @Resource
    private LiveRoomMapper liveRoomMapper;
    @Resource
    private LiveDanmakuMapper liveDanmakuMapper;
    @Resource
    private LearningConfigService learningConfigService;
    @Resource
    private LearnerSessionService learnerSessionService;

    @Override
    public LiveRoomGroupRespVO getRoomGroups() {
        LiveRoomGroupRespVO respVO = new LiveRoomGroupRespVO();
        respVO.setUpcoming(convert(liveRoomMapper.selectListByStatus(STATUS_UPCOMING)));
        respVO.setLive(convert(liveRoomMapper.selectListByStatus(STATUS_LIVING)));
        respVO.setEnded(convert(liveRoomMapper.selectListByStatus(STATUS_ENDED)));
        return respVO;
    }

    @Override
    public PortalLiveRoomVO getRoomDetail(Long roomId) {
        LiveRoomDO liveRoom = liveRoomMapper.selectById(roomId);
        if (liveRoom == null) {
            throw exception(ErrorCodeConstants.LEARNER_LIVE_ROOM_NOT_EXISTS);
        }
        return convert(liveRoom);
    }

    @Override
    public void sendDanmaku(Long userId, String userName, Long roomId, String content) {
        LiveRoomDO liveRoom = liveRoomMapper.selectById(roomId);
        if (liveRoom == null) {
            throw exception(ErrorCodeConstants.LEARNER_LIVE_ROOM_NOT_EXISTS);
        }
        if (!learningConfigService.getBoolean(LearningConfigService.KEY_DANMAKU_ENABLED, true)) {
            throw exception(ErrorCodeConstants.LEARNER_DANMAKU_DISABLED);
        }
        LiveDanmakuDO danmaku = new LiveDanmakuDO();
        danmaku.setRoomId(roomId);
        danmaku.setUserId(userId);
        danmaku.setUserName(userName);
        danmaku.setContent(content);
        danmaku.setCreateAt(LocalDateTime.now());
        liveDanmakuMapper.insert(danmaku);
        log.info("[sendDanmaku][用户 {} 房间 {} 发送弹幕]", userId, roomId);
    }

    @Override
    public List<LiveDanmakuRespVO> getDanmakuList(Long roomId, Long afterId) {
        List<LiveDanmakuDO> list = liveDanmakuMapper.selectListAfterId(roomId,
                afterId == null ? 0L : afterId, 100);
        List<LiveDanmakuRespVO> result = new ArrayList<>(list.size());
        for (LiveDanmakuDO danmaku : list) {
            LiveDanmakuRespVO respVO = new LiveDanmakuRespVO();
            respVO.setId(danmaku.getId());
            respVO.setRoomId(danmaku.getRoomId());
            respVO.setUserId(danmaku.getUserId());
            respVO.setUserName(danmaku.getUserName());
            respVO.setContent(danmaku.getContent());
            respVO.setCreateAt(danmaku.getCreateAt());
            result.add(respVO);
        }
        return result;
    }

    // ========== 私有方法 ==========

    private List<PortalLiveRoomVO> convert(List<LiveRoomDO> rooms) {
        List<PortalLiveRoomVO> result = new ArrayList<>(rooms.size());
        for (LiveRoomDO room : rooms) {
            result.add(convert(room));
        }
        return result;
    }

    private PortalLiveRoomVO convert(LiveRoomDO room) {
        PortalLiveRoomVO respVO = new PortalLiveRoomVO();
        respVO.setId(room.getId());
        respVO.setTitle(room.getTitle());
        respVO.setCoverUrl(room.getCoverUrl());
        respVO.setLecturerName(room.getLecturerName());
        respVO.setStatus(room.getStatus());
        respVO.setStatusText(statusText(room.getStatus()));
        respVO.setPlanStartAt(room.getPlanStartAt());
        respVO.setPlanEndAt(room.getPlanEndAt());
        respVO.setStreamUrl(room.getStreamUrl());
        respVO.setReplayVideoUrl(room.getReplayVideoUrl());
        respVO.setCreditHours(room.getCreditHours());
        respVO.setViewerCount(learnerSessionService.countActiveViewers(room.getId()));
        respVO.setCanJoin(room.getStatus() != null && room.getStatus() == STATUS_LIVING);
        respVO.setCanReplay(room.getStatus() != null && room.getStatus() == STATUS_ENDED
                && StringUtils.hasText(room.getReplayVideoUrl()));
        respVO.setRemark(room.getRemark());
        return respVO;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_UPCOMING -> "未开始";
            case STATUS_LIVING -> "直播中";
            case STATUS_ENDED -> "已结束";
            default -> "未知";
        };
    }

}
