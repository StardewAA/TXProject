package cn.iocoder.txgy.module.bidding.dal.mysql.prequal;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalPageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 资格预审主记录 Mapper。
 */
@Mapper
public interface PrequalMapper extends BaseMapperX<PrequalDO> {

    /** 按资格预审编号取记录（业务唯一键）。 */
    default PrequalDO selectByCode(String prequalCode) {
        return selectOne(PrequalDO::getPrequalCode, prequalCode);
    }

    /** 按招标项目查资格预审列表。 */
    default List<PrequalDO> selectListByTenderProject(String tenderProjectCode) {
        return selectList(PrequalDO::getTenderProjectCode, tenderProjectCode);
    }

    /** 管理端分页（可按状态/招标项目过滤）。 */
    default PageResult<PrequalDO> selectPage(PrequalPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<PrequalDO>()
                .likeIfPresent(PrequalDO::getTitle, reqVO.getTitle())
                .eqIfPresent(PrequalDO::getTenderProjectCode, reqVO.getTenderProjectCode())
                .eqIfPresent(PrequalDO::getBidSectionCode, reqVO.getBidSectionCode())
                .eqIfPresent(PrequalDO::getStatus, reqVO.getStatus())
                .orderByDesc(PrequalDO::getCreateTime));
    }
}
