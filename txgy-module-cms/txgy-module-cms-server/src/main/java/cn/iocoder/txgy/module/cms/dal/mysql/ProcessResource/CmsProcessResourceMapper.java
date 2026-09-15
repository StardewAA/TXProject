package cn.iocoder.txgy.module.cms.dal.mysql.ProcessResource;

import cn.iocoder.txgy.module.cms.controller.admin.ProcessResource.vo.CmsProcessResourcePageReqVO;
import cn.iocoder.txgy.module.cms.dal.dataobject.ProcessResource.CmsProcessResourceDO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工艺工法与案例库 Mapper
 *
 * @author yuting_feng
 */
@Mapper
public interface CmsProcessResourceMapper extends BaseMapperX<CmsProcessResourceDO> {

    default PageResult<CmsProcessResourceDO> selectPage(CmsProcessResourcePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CmsProcessResourceDO>()
        // 二期调整2：列表接口排除 content 大字段（select 置于链尾，避免改变返回类型），详情接口单独查全文
                .likeIfPresent(CmsProcessResourceDO::getTitle, reqVO.getTitle())
                .likeIfPresent(CmsProcessResourceDO::getAuthor, reqVO.getAuthor())
                .eqIfPresent(CmsProcessResourceDO::getResourceType, reqVO.getResourceType())
                .eqIfPresent(CmsProcessResourceDO::getDifficulty, reqVO.getDifficulty())
                .eqIfPresent(CmsProcessResourceDO::getIsFree, reqVO.getIsFree())
                .eqIfPresent(CmsProcessResourceDO::getAuditStatus, reqVO.getAuditStatus())
                .eqIfPresent(CmsProcessResourceDO::getPublishStatus, reqVO.getPublishStatus())
                .orderByDesc(CmsProcessResourceDO::getResourceId)
                .select(CmsProcessResourceDO::getResourceId, CmsProcessResourceDO::getTitle, CmsProcessResourceDO::getResourceType, CmsProcessResourceDO::getIndustryField, CmsProcessResourceDO::getProtectObject, CmsProcessResourceDO::getScene, CmsProcessResourceDO::getDifficulty, CmsProcessResourceDO::getProcessSteps, CmsProcessResourceDO::getVideoId, CmsProcessResourceDO::getAttachmentIds, CmsProcessResourceDO::getRelatedStandardIds, CmsProcessResourceDO::getRelatedMaterialIds, CmsProcessResourceDO::getSource, CmsProcessResourceDO::getAuthor, CmsProcessResourceDO::getIsFree, CmsProcessResourceDO::getViewCount, CmsProcessResourceDO::getLikeCount, CmsProcessResourceDO::getCollectCount, CmsProcessResourceDO::getAuditStatus, CmsProcessResourceDO::getAuditRemark, CmsProcessResourceDO::getPublishStatus, CmsProcessResourceDO::getPublishTime, CmsProcessResourceDO::getCreator, CmsProcessResourceDO::getCreateTime, CmsProcessResourceDO::getUpdater, CmsProcessResourceDO::getUpdateTime, CmsProcessResourceDO::getTenantId, CmsProcessResourceDO::getDeleted));
    }

}
