package cn.iocoder.txgy.module.bidding.dal.mysql.flow;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.flow.FlowLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 招标全流程状态流转留痕 Mapper（G 轨，审计只追加）。
 */
@Mapper
public interface FlowLogMapper extends BaseMapperX<FlowLogDO> {

    default List<FlowLogDO> selectListByBiz(String bizType, String bizCode) {
        return selectList(new LambdaQueryWrapperX<FlowLogDO>()
                .eq(FlowLogDO::getBizType, bizType)
                .eq(FlowLogDO::getBizCode, bizCode)
                .orderByDesc(FlowLogDO::getId));
    }
}
