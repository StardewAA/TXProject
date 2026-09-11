package cn.iocoder.txgy.module.training.service.learning;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LiveRoomPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LiveRoomSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LiveRoomDO;

/**
 * 直播房间 Service 接口
 *
 * @author 云培训学院课题小组
 */
public interface LiveRoomService {

    /**
     * 创建直播房间
     *
     * @param createReqVO 创建信息
     * @return 房间编号
     */
    Long createLiveRoom(LiveRoomSaveReqVO createReqVO);

    /**
     * 更新直播房间
     *
     * @param updateReqVO 更新信息
     */
    void updateLiveRoom(LiveRoomSaveReqVO updateReqVO);

    /**
     * 删除直播房间
     *
     * @param id 房间编号
     */
    void deleteLiveRoom(Long id);

    /**
     * 获得直播房间
     *
     * @param id 房间编号
     * @return 直播房间
     */
    LiveRoomDO getLiveRoom(Long id);

    /**
     * 获得直播房间分页
     *
     * @param pageReqVO 分页查询
     * @return 直播房间分页
     */
    PageResult<LiveRoomDO> getLiveRoomPage(LiveRoomPageReqVO pageReqVO);

}
