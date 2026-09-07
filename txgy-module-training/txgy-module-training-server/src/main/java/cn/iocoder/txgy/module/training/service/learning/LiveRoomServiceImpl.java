package cn.iocoder.txgy.module.training.service.learning;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LiveRoomPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LiveRoomSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LiveRoomDO;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LiveRoomMapper;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 直播房间 Service 实现
 *
 * @author 云培训学院课题小组
 */
@Service
@Validated
public class LiveRoomServiceImpl implements LiveRoomService {

    @Resource
    private LiveRoomMapper liveRoomMapper;
    @Resource private cn.iocoder.txgy.module.training.service.live.LiveCenterService liveCenter;

    @Override
    public Long createLiveRoom(LiveRoomSaveReqVO createReqVO) {
        liveCenter.rejectLegacyMutation();
        LiveRoomDO liveRoom = BeanUtils.toBean(createReqVO, LiveRoomDO.class);
        liveRoomMapper.insert(liveRoom);
        return liveRoom.getId();
    }

    @Override
    public void updateLiveRoom(LiveRoomSaveReqVO updateReqVO) {
        liveCenter.rejectLegacyMutation();
        validateLiveRoomExists(updateReqVO.getId());
        LiveRoomDO update = BeanUtils.toBean(updateReqVO, LiveRoomDO.class);
        liveRoomMapper.updateById(update);
    }

    @Override
    public void deleteLiveRoom(Long id) {
        liveCenter.manage(id);
        validateLiveRoomExists(id);
        liveRoomMapper.deleteById(id);
    }

    private void validateLiveRoomExists(Long id) {
        if (liveRoomMapper.selectById(id) == null) {
            throw exception(ErrorCodeConstants.LEARNER_LIVE_ROOM_NOT_EXISTS);
        }
    }

    @Override
    public LiveRoomDO getLiveRoom(Long id) {
        return liveRoomMapper.selectById(id);
    }

    @Override
    public PageResult<LiveRoomDO> getLiveRoomPage(LiveRoomPageReqVO pageReqVO) {
        return liveRoomMapper.selectPage(pageReqVO);
    }

}
