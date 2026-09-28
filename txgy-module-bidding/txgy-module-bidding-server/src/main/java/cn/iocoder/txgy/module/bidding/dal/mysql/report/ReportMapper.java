package cn.iocoder.txgy.module.bidding.dal.mysql.report;

// 规范依据：《招投标模块开发规范》§5 数据层（Mapper 继承 BaseMapperX，单表零 SQL 优先）

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.report.vo.ReportPageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.report.ReportDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定标书面报告 Mapper。
 *
 * <p>业务目标：报告提交/查询/分页。
 */
@Mapper
public interface ReportMapper extends BaseMapperX<ReportDO> {

    /** 报告台账分页（按招标项目/标段过滤）。 */
    default PageResult<ReportDO> selectPage(ReportPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ReportDO>()
                .eqIfPresent(ReportDO::getTenderProjectCode, reqVO.getTenderProjectCode())
                .eqIfPresent(ReportDO::getBidSectionCode, reqVO.getBidSectionCode())
                .orderByDesc(ReportDO::getId));
    }
}
