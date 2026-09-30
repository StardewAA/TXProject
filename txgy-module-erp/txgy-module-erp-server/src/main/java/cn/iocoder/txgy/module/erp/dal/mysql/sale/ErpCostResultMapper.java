package cn.iocoder.txgy.module.erp.dal.mysql.sale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.cost.ErpCostResultPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCostResultDO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpCostResultMapper extends BaseMapperX<ErpCostResultDO> {

    /**
     * 反审批：回退为草稿（审批状态=0、业务状态=0），并清空 BPM 流程实例编号
     */
    default int reverseApproveByIdAndStatus(Long id, Integer status, String reason) {
        return update(new LambdaUpdateWrapper<ErpCostResultDO>()
                .eq(ErpCostResultDO::getId, id).eq(ErpCostResultDO::getStatus, status)
                .set(ErpCostResultDO::getStatus, ErpAuditStatus.DRAFT.getStatus())
                .set(ErpCostResultDO::getBusinessStatus, 0)
                .set(ErpCostResultDO::getProcessInstanceId, null)
                .set(ErpCostResultDO::getReverseApproveReason, reason));
    }
    default PageResult<ErpCostResultDO> selectPage(ErpCostResultPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpCostResultDO>()
                .likeIfPresent(ErpCostResultDO::getNo, reqVO.getNo())
                .likeIfPresent(ErpCostResultDO::getName, reqVO.getName())
                .likeIfPresent(ErpCostResultDO::getProjectCode, reqVO.getProjectCode())
                .eqIfPresent(ErpCostResultDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ErpCostResultDO::getBusinessStatus, reqVO.getBusinessStatus())
                .orderByDesc(ErpCostResultDO::getId));
    }
    default ErpCostResultDO selectByNo(String no) { return selectOne(ErpCostResultDO::getNo, no); }
    default ErpCostResultDO selectByProjectAndVersion(String projectCode, Integer version) {
        return selectOne(ErpCostResultDO::getProjectCode, projectCode, ErpCostResultDO::getCostVersion, version);
    }
}
