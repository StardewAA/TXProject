package cn.iocoder.txgy.module.training.service.learner;

import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LiveDanmakuRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LiveRoomGroupRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.PortalLiveRoomVO;

import java.util.List;

/**
 * 学员端 - 直播/弹幕 Service 接口
 *
 * @author 云培训学院课题小组
 */
public interface LearnerLiveService {

    /**
     * 获得直播大厅（按 未开始/直播中/已结束 分组）
     */
    LiveRoomGroupRespVO getRoomGroups();

    /**
     * 获得直播房间详情
     *
     * @param roomId 房间 ID
     */
    PortalLiveRoomVO getRoomDetail(Long roomId);

    /**
     * 发送直播弹幕（需开启弹幕配置）
     *
     * @param userId   发送人用户 ID
     * @param userName 发送人昵称快照
     * @param roomId   房间 ID
     * @param content  内容
     */
    void sendDanmaku(Long userId, String userName, Long roomId, String content);

    /**
     * 拉取房间弹幕（增量）
     *
     * @param roomId  房间 ID
     * @param afterId 仅返回 id 大于该值的弹幕，可为 null（首次拉取）
     */
    List<LiveDanmakuRespVO> getDanmakuList(Long roomId, Long afterId);

}
