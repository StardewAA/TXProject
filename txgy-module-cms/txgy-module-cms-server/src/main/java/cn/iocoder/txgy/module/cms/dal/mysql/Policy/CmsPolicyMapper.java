package cn.iocoder.txgy.module.cms.dal.mysql.Policy;

import cn.iocoder.txgy.module.cms.controller.admin.Policy.vo.CmsPolicyPageReqVO;
import cn.iocoder.txgy.module.cms.dal.dataobject.Policy.CmsPolicyDO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 政策资讯 Mapper
 *
 * @author yuting_feng
 */
@Mapper
public interface CmsPolicyMapper extends BaseMapperX<CmsPolicyDO> {

    default PageResult<CmsPolicyDO> selectPage(CmsPolicyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CmsPolicyDO>()
        // 二期调整2：列表接口排除 content 大字段（select 置于链尾，避免改变返回类型），详情接口单独查全文
                .likeIfPresent(CmsPolicyDO::getTitle, reqVO.getTitle())
                .likeIfPresent(CmsPolicyDO::getPublishDept, reqVO.getPublishDept())
                .likeIfPresent(CmsPolicyDO::getSourceName, reqVO.getSourceName())
                .eqIfPresent(CmsPolicyDO::getPolicyLevel, reqVO.getPolicyLevel())
                .eqIfPresent(CmsPolicyDO::getCategory, reqVO.getCategory())
                .eqIfPresent(CmsPolicyDO::getContentType, reqVO.getContentType())
                .eqIfPresent(CmsPolicyDO::getEffectiveStatus, reqVO.getEffectiveStatus())
                .eqIfPresent(CmsPolicyDO::getAuditStatus, reqVO.getAuditStatus())
                .eqIfPresent(CmsPolicyDO::getPublishStatus, reqVO.getPublishStatus())
                .orderByDesc(CmsPolicyDO::getPolicyId)
                .select(CmsPolicyDO::getPolicyId, CmsPolicyDO::getTitle, CmsPolicyDO::getPolicyLevel, CmsPolicyDO::getPublishDept, CmsPolicyDO::getCategory, CmsPolicyDO::getEffectiveStatus, CmsPolicyDO::getPublishDate, CmsPolicyDO::getEffectiveDate, CmsPolicyDO::getImplementDate, CmsPolicyDO::getSourceUrl, CmsPolicyDO::getSourceName, CmsPolicyDO::getAttachmentUrls, CmsPolicyDO::getContentType, CmsPolicyDO::getSummary, CmsPolicyDO::getViewCount, CmsPolicyDO::getDownloadCount, CmsPolicyDO::getIsTop, CmsPolicyDO::getAuditStatus, CmsPolicyDO::getAuditRemark, CmsPolicyDO::getPublishStatus, CmsPolicyDO::getPublishTime, CmsPolicyDO::getCreator, CmsPolicyDO::getCreateTime, CmsPolicyDO::getUpdater, CmsPolicyDO::getUpdateTime, CmsPolicyDO::getTenantId, CmsPolicyDO::getDeleted));
    }

}
