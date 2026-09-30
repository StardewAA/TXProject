package cn.iocoder.txgy.module.bidding.dal.mysql.flow;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.flow.FlowTimerDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 招标全流程时间驱动调度记录 Mapper（G 轨）。
 */
@Mapper
public interface FlowTimerMapper extends BaseMapperX<FlowTimerDO> {

    /** 查询已到期且待触发的记录（调度器扫描；轻量索引行）。 */
    default List<FlowTimerDO> selectListByDue(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<FlowTimerDO>()
                .eq(FlowTimerDO::getStatus, "WAITING")
                .le(FlowTimerDO::getTriggerTime, now)
                .orderByAsc(FlowTimerDO::getTriggerTime));
    }

    /** 查询某实体全部调度记录（按触发时间倒序）。 */
    default List<FlowTimerDO> selectListByBiz(String bizType, String bizCode) {
        return selectList(new LambdaQueryWrapperX<FlowTimerDO>()
                .eq(FlowTimerDO::getBizType, bizType)
                .eq(FlowTimerDO::getBizCode, bizCode)
                .orderByDesc(FlowTimerDO::getTriggerTime));
    }

    /** 查询同实体同事件的 WAITING 记录（save 注册时幂等更新用）。 */
    default FlowTimerDO selectWaiting(String bizType, String bizCode, String event) {
        return selectOne(new LambdaQueryWrapperX<FlowTimerDO>()
                .eq(FlowTimerDO::getBizType, bizType)
                .eq(FlowTimerDO::getBizCode, bizCode)
                .eq(FlowTimerDO::getEvent, event)
                .eq(FlowTimerDO::getStatus, "WAITING")
                .last("LIMIT 1"));
    }
}
