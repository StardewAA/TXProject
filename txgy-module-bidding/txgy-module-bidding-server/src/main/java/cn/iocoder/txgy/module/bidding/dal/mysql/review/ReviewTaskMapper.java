package cn.iocoder.txgy.module.bidding.dal.mysql.review;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.review.ReviewTaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台审查任务 Mapper。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§2/§5。
 * <p>为什么用 BaseMapperX：与全仓一致（分页、逻辑删除、租户条件由框架统一处理）。
 */
@Mapper
public interface ReviewTaskMapper extends BaseMapperX<ReviewTaskDO> {
}
