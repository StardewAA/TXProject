package cn.iocoder.txgy.module.crm.dal.mysql.quotation;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationPageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.quotation.CrmQuotationDO;
import cn.iocoder.txgy.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.txgy.module.crm.util.CrmPermissionUtils;
import org.apache.ibatis.annotations.Mapper;

/**
 * CRM 报价单 Mapper
 *
 * @author 防腐保温项目组
 */
@Mapper
public interface CrmQuotationMapper extends BaseMapperX<CrmQuotationDO> {

    default CrmQuotationDO selectByNo(String no) {
        return selectOne(CrmQuotationDO::getNo, no);
    }

    default PageResult<CrmQuotationDO> selectPage(CrmQuotationPageReqVO pageReqVO, Long userId) {
        MPJLambdaWrapperX<CrmQuotationDO> query = new MPJLambdaWrapperX<>();
        // 拼接数据权限的查询条件
        CrmPermissionUtils.appendPermissionCondition(query, CrmBizTypeEnum.CRM_QUOTATION.getType(),
                CrmQuotationDO::getId, userId, pageReqVO.getSceneType());
        // 拼接自身的查询条件
        query.selectAll(CrmQuotationDO.class)
                .likeIfPresent(CrmQuotationDO::getNo, pageReqVO.getNo())
                .likeIfPresent(CrmQuotationDO::getName, pageReqVO.getName())
                .eqIfPresent(CrmQuotationDO::getCustomerId, pageReqVO.getCustomerId())
                .eqIfPresent(CrmQuotationDO::getBusinessId, pageReqVO.getBusinessId())
                .eqIfPresent(CrmQuotationDO::getAuditStatus, pageReqVO.getAuditStatus())
                .orderByDesc(CrmQuotationDO::getId);
        return selectJoinPage(pageReqVO, CrmQuotationDO.class, query);
    }

    default Long selectCountByCustomerId(Long customerId) {
        return selectCount(CrmQuotationDO::getCustomerId, customerId);
    }

    default Long selectCountByBusinessId(Long businessId) {
        return selectCount(CrmQuotationDO::getBusinessId, businessId);
    }

}
