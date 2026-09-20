package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.request.ErpPurchaseRequestPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseRequestDO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpPurchaseRequestMapper extends BaseMapperX<ErpPurchaseRequestDO> {

    /**
     * 反审批：将指定状态的采购申请回退为草稿，并清空 BPM 流程实例编号
     */
    default int reverseApproveByIdAndStatus(Long id, Integer status, String reason) {
        return update(new LambdaUpdateWrapper<ErpPurchaseRequestDO>()
                .eq(ErpPurchaseRequestDO::getId, id).eq(ErpPurchaseRequestDO::getStatus, status)
                .set(ErpPurchaseRequestDO::getStatus, ErpAuditStatus.DRAFT.getStatus())
                .set(ErpPurchaseRequestDO::getProcessInstanceId, null)
                .set(ErpPurchaseRequestDO::getReverseApproveReason, reason));
    }
    default PageResult<ErpPurchaseRequestDO> selectPage(ErpPurchaseRequestPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpPurchaseRequestDO>()
                .likeIfPresent(ErpPurchaseRequestDO::getNo, reqVO.getNo())
                .eqIfPresent(ErpPurchaseRequestDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ErpPurchaseRequestDO::getDepartmentId, reqVO.getDepartmentId())
                .betweenIfPresent(ErpPurchaseRequestDO::getRequestTime, reqVO.getRequestTime())
                .likeIfPresent(ErpPurchaseRequestDO::getPurpose, reqVO.getPurpose())
                .orderByDesc(ErpPurchaseRequestDO::getId));
    }
    default ErpPurchaseRequestDO selectByNo(String no) { return selectOne(ErpPurchaseRequestDO::getNo, no); }
}
