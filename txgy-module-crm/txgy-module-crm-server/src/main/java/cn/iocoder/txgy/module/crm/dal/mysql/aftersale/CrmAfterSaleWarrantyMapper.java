package cn.iocoder.txgy.module.crm.dal.mysql.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantyPageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleWarrantyStatusEnum;
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

    /**
     * 按合同编号查询质保档案
     *
     * <p>业务约束：一个合同仅允许一份有效质保档案（一对一）。
     * 此处刻意使用 selectList 而不是 selectOne —— 历史数据若存在重复，
     * selectOne 会直接抛 TooManyResultsException，导致「创建时给出友好提示」变成 500。
     * 返回最早创建的一条（id 升序），与其他查询口径一致。
     *
     * @param contractId 合同编号
     * @return 质保档案；不存在时返回 null
     */
    default CrmAfterSaleWarrantyDO selectByContractId(Long contractId) {
        if (contractId == null) {
            return null;
        }
        return CollUtil.getFirst(selectList(new LambdaQueryWrapperX<CrmAfterSaleWarrantyDO>()
                .eq(CrmAfterSaleWarrantyDO::getContractId, contractId)
                .orderByAsc(CrmAfterSaleWarrantyDO::getId)));
    }

    /**
     * 按合同编号集合批量查询质保档案（用于「合同是否已创建质保档案」的状态展示 / 校验）
     *
     * @param contractIds 合同编号集合
     * @return 质保档案列表
     */
    default List<CrmAfterSaleWarrantyDO> selectListByContractIds(Collection<Long> contractIds) {
        if (CollUtil.isEmpty(contractIds)) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleWarrantyDO>()
                .in(CrmAfterSaleWarrantyDO::getContractId, contractIds)
                .orderByAsc(CrmAfterSaleWarrantyDO::getId));
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

    // ========== 待办：质保即将到期（实时查询，不落库） ==========

    /**
     * 待办「质保即将到期」分页查询
     *
     * <p>实时判定条件：
     * <ul>
     *   <li>质保状态 = 质保中（作废 / 已到期终止后自然不再命中，刷新即消失）；</li>
     *   <li>售后责任人 ∈ 归属范围（当前登录人本人 + 其下属）；</li>
     *   <li>质保到期日 ∈ [今天, 今天 + 售后设置「质保到期提前提醒天数」]。</li>
     * </ul>
     * 按质保到期日升序，最紧急的排在最前。
     *
     * @param reqVO          分页查询
     * @param serviceUserIds 归属范围（本人 + 下属负责的售后责任人）
     * @param today          当前日期
     * @param warnDays       售后设置「质保到期提前提醒天数」
     */
    default PageResult<CrmAfterSaleWarrantyDO> selectPageByExpiry(CrmAfterSaleWarrantyPageReqVO reqVO,
                                                                 Collection<Long> serviceUserIds, LocalDate today, Integer warnDays) {
        if (CollUtil.isEmpty(serviceUserIds)) {
            return PageResult.empty();
        }
        int days = warnDays == null || warnDays < 0 ? 0 : warnDays;
        return selectPage(reqVO, new LambdaQueryWrapperX<CrmAfterSaleWarrantyDO>()
                .eq(CrmAfterSaleWarrantyDO::getStatus, CrmAfterSaleWarrantyStatusEnum.IN_WARRANTY.getStatus())
                .in(CrmAfterSaleWarrantyDO::getServiceUserId, serviceUserIds)
                .between(CrmAfterSaleWarrantyDO::getWarrantyEndDate, today, today.plusDays(days))
                .orderByAsc(CrmAfterSaleWarrantyDO::getWarrantyEndDate));
    }

    /**
     * 待办「质保即将到期」数量（与 {@link #selectPageByExpiry} 同一口径）
     */
    default Long selectCountByExpiry(Collection<Long> serviceUserIds, LocalDate today, Integer warnDays) {
        if (CollUtil.isEmpty(serviceUserIds)) {
            return 0L;
        }
        int days = warnDays == null || warnDays < 0 ? 0 : warnDays;
        return selectCount(new LambdaQueryWrapperX<CrmAfterSaleWarrantyDO>()
                .eq(CrmAfterSaleWarrantyDO::getStatus, CrmAfterSaleWarrantyStatusEnum.IN_WARRANTY.getStatus())
                .in(CrmAfterSaleWarrantyDO::getServiceUserId, serviceUserIds)
                .between(CrmAfterSaleWarrantyDO::getWarrantyEndDate, today, today.plusDays(days)));
    }

}
