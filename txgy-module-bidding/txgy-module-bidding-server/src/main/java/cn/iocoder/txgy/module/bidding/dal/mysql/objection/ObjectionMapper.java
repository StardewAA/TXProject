package cn.iocoder.txgy.module.bidding.dal.mysql.objection;

// 规范依据：《招投标模块开发规范》§5 数据层（Mapper 继承 BaseMapperX，单表零 SQL 优先）

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionPageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.objection.ObjectionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 投标异议 Mapper。
 *
 * <p>业务目标：异议提出/答复/台账查询。
 */
@Mapper
public interface ObjectionMapper extends BaseMapperX<ObjectionDO> {

    /** 异议台账分页（按招标项目/标段/类型过滤）。 */
    default PageResult<ObjectionDO> selectPage(ObjectionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ObjectionDO>()
                .eqIfPresent(ObjectionDO::getTenderProjectCode, reqVO.getTenderProjectCode())
                .eqIfPresent(ObjectionDO::getBidSectionCode, reqVO.getBidSectionCode())
                .eqIfPresent(ObjectionDO::getObjectionType, reqVO.getObjectionType())
                .orderByDesc(ObjectionDO::getId));
    }
}
