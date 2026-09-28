package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry.ErpInquiryPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpInquiryDO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;

@Mapper
public interface ErpInquiryMapper extends BaseMapperX<ErpInquiryDO> {

    /**
     * 询价反审批：回退为草稿（审批状态=0、业务阶段=0 草稿），并清空询价流程实例编号
     */
    default int reverseApproveInquiryByIdAndStatus(Long id, Integer status, String reason) {
        return update(new LambdaUpdateWrapper<ErpInquiryDO>()
                .eq(ErpInquiryDO::getId, id).eq(ErpInquiryDO::getStatus, status)
                .set(ErpInquiryDO::getStatus, ErpAuditStatus.DRAFT.getStatus())
                .set(ErpInquiryDO::getBusinessStatus, 0)
                .set(ErpInquiryDO::getProcessInstanceId, null)
                .set(ErpInquiryDO::getReverseApproveReason, reason));
    }

    /**
     * 定标反审批：定标审批状态回退为草稿、业务阶段回退到 40（比价中），并清空定标流程实例编号
     */
    default int reverseApproveAwardByIdAndStatus(Long id, Integer awardStatus, String reason) {
        return update(new LambdaUpdateWrapper<ErpInquiryDO>()
                .eq(ErpInquiryDO::getId, id).eq(ErpInquiryDO::getAwardStatus, awardStatus)
                .set(ErpInquiryDO::getAwardStatus, ErpAuditStatus.DRAFT.getStatus())
                .set(ErpInquiryDO::getBusinessStatus, 40)
                .set(ErpInquiryDO::getAwardProcessInstanceId, null)
                .set(ErpInquiryDO::getAwardReverseApproveReason, reason));
    }

    /**
     * 构造询价分页查询条件（抽出来是为了让「供应商视角」能复用同一套过滤条件）。
     */
    default LambdaQueryWrapperX<ErpInquiryDO> buildQuery(ErpInquiryPageReqVO reqVO) {
        LambdaQueryWrapperX<ErpInquiryDO> query = new LambdaQueryWrapperX<ErpInquiryDO>()
                .likeIfPresent(ErpInquiryDO::getNo, reqVO.getNo())
                .likeIfPresent(ErpInquiryDO::getTitle, reqVO.getTitle())
                .eqIfPresent(ErpInquiryDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ErpInquiryDO::getBusinessStatus, reqVO.getBusinessStatus())
                .eqIfPresent(ErpInquiryDO::getAwardStatus, reqVO.getAwardStatus())
                .eqIfPresent(ErpInquiryDO::getInquiryMode, reqVO.getInquiryMode())
                .betweenIfPresent(ErpInquiryDO::getDeadline, reqVO.getDeadline());
        if (Boolean.TRUE.equals(reqVO.getAwardOnly())) {
            query.ge(ErpInquiryDO::getBusinessStatus, 40);
        }
        return query;
    }

    default PageResult<ErpInquiryDO> selectPage(ErpInquiryPageReqVO reqVO) {
        return selectPage(reqVO, buildQuery(reqVO).orderByDesc(ErpInquiryDO::getId));
    }

    /**
     * 供应商视角分页：只查询受邀的询价单。
     *
     * @param inquiryIds 受邀的询价单编号；为空表示没有受邀任何询价单，直接返回空页
     */
    default PageResult<ErpInquiryDO> selectPageByInquiryIds(ErpInquiryPageReqVO reqVO, Collection<Long> inquiryIds) {
        if (CollUtil.isEmpty(inquiryIds)) {
            return PageResult.empty();
        }
        return selectPage(reqVO, buildQuery(reqVO).in(ErpInquiryDO::getId, inquiryIds)
                .orderByDesc(ErpInquiryDO::getId));
    }

    default ErpInquiryDO selectByNo(String no) { return selectOne(ErpInquiryDO::getNo, no); }
}
