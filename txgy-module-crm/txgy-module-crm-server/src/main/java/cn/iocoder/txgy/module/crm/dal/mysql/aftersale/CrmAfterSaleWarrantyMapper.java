package cn.iocoder.txgy.module.crm.dal.mysql.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantyPageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 售后质保档案 Mapper
 *
 * @author txgycloud
 */
@Mapper
public interface CrmAfterSaleWarrantyMapper extends BaseMapperX<CrmAfterSaleWarrantyDO> {

    default CrmAfterSaleWarrantyDO selectByNo(String no) {
        return selectOne(CrmAfterSaleWarrantyDO::getNo, no);
    }

    default PageResult<CrmAfterSaleWarrantyDO> selectPage(CrmAfterSaleWarrantyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CrmAfterSaleWarrantyDO>()
                .likeIfPresent(CrmAfterSaleWarrantyDO::getNo, reqVO.getNo())
                .likeIfPresent(CrmAfterSaleWarrantyDO::getProjectName, reqVO.getProjectName())
                .eqIfPresent(CrmAfterSaleWarrantyDO::getContractId, reqVO.getContractId())
                .eqIfPresent(CrmAfterSaleWarrantyDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(CrmAfterSaleWarrantyDO::getStatus, reqVO.getStatus())
                .eqIfPresent(CrmAfterSaleWarrantyDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(CrmAfterSaleWarrantyDO::getServiceUserId, reqVO.getServiceUserId())
                .betweenIfPresent(CrmAfterSaleWarrantyDO::getWarrantyEndDate, reqVO.getWarrantyEndDate())
                .orderByDesc(CrmAfterSaleWarrantyDO::getId));
    }

    default List<CrmAfterSaleWarrantyDO> selectListByStatusAndEndDate(Integer status, LocalDate endDate) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleWarrantyDO>()
                .eq(CrmAfterSaleWarrantyDO::getStatus, status)
                .le(CrmAfterSaleWarrantyDO::getWarrantyEndDate, endDate));
    }

    default List<CrmAfterSaleWarrantyDO> selectListByStatusBetween(Integer status, LocalDate begin, LocalDate end) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleWarrantyDO>()
                .eq(CrmAfterSaleWarrantyDO::getStatus, status)
                .between(CrmAfterSaleWarrantyDO::getWarrantyEndDate, begin, end));
    }

    /**
     * 查询指定状态下（未删除）的质保档案列表
     */
    default List<CrmAfterSaleWarrantyDO> selectListByStatusIn(Collection<Integer> statuses) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleWarrantyDO>()
                .in(CrmAfterSaleWarrantyDO::getStatus, statuses));
    }

    /**
     * 查询指定状态、且质保到期日不晚于指定日期的质保档案列表
     */
    default List<CrmAfterSaleWarrantyDO> selectListByStatusInAndEndDateLe(Collection<Integer> statuses, LocalDate endDate) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleWarrantyDO>()
                .in(CrmAfterSaleWarrantyDO::getStatus, statuses)
                .le(CrmAfterSaleWarrantyDO::getWarrantyEndDate, endDate));
    }

    default List<CrmAfterSaleWarrantyDO> selectListByIds(Collection<Long> ids) {
        return selectBatchIds(ids);
    }

}
