package cn.iocoder.txgy.module.training.service.learner;

import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionEndRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionHeartbeatRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionStartReqVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionStartRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionVerifyReqVO;

import java.util.Map;

/**
 * 学习会话服务（防挂机核心）
 *
 * <p>会话生命周期：start → heartbeat(5s) → [verify] → end/checkin。
 * 有效学时只按服务端心跳时间差（delta）累计，与播放器进度/后台计时解耦，
 * 从机制上规避「挂机/切后台/拖动进度条」刷学时。</p>
 *
 * @author 云培训学院课题小组
 */
public interface LearnerSessionService {

    /**
     * 开启学习会话
     *
     * @throws cn.iocoder.txgy.framework.common.exception.ServiceException 课程未上架/媒资不可学/存在进行中会话等
     */
    LearnerSessionStartRespVO start(Long userId, LearnerSessionStartReqVO reqVO);

    /**
     * 学习心跳：服务端校验时间差并累计有效学时；文档会话顺带上报当前页
     *
     * @param pageNo     当前页（document 会话必传）
     * @param totalPages 文档总页数（首次上报后服务端记录）
     */
    LearnerSessionHeartbeatRespVO heartbeat(Long userId, Long sessionId, Integer pageNo, Integer totalPages);

    /**
     * 人机验证：回答挑战题（失败 3 次会话判无效）
     */
    void verify(Long userId, LearnerSessionVerifyReqVO reqVO);

    /**
     * 结束会话并结算（正常退出）
     */
    LearnerSessionEndRespVO end(Long userId, Long sessionId);

    /**
     * 会话打卡（有效学时 ≥15 秒可打一次）
     */
    LearnerSessionEndRespVO checkin(Long userId, Long sessionId);

    /**
     * 会话参数（心跳间隔/超时/验证间隔等），供学员端定时器与界面提示
     */
    Map<String, Object> getSessionConfig();

    /**
     * 某直播房间当前活跃观看人数（内存态，未精确至分秒）
     */
    long countActiveViewers(Long roomId);

}
