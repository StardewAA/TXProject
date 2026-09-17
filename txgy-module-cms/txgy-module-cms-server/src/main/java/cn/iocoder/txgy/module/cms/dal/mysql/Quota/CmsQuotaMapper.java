package cn.iocoder.txgy.module.cms.dal.mysql.Quota;

import cn.iocoder.txgy.module.cms.controller.admin.Quota.vo.CmsQuotaPageReqVO;
import cn.iocoder.txgy.module.cms.dal.dataobject.Quota.CmsQuotaDO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资质定额资料库 Mapper
 *
 * @author yuting_feng
 */
@Mapper
public interface CmsQuotaMapper extends BaseMapperX<CmsQuotaDO> {

    default PageResult<CmsQuotaDO> selectPage(CmsQuotaPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CmsQuotaDO>()
                .likeIfPresent(CmsQuotaDO::getTitle, reqVO.getTitle())
                .eqIfPresent(CmsQuotaDO::getQuotaType, reqVO.getQuotaType())
                .eqIfPresent(CmsQuotaDO::getRegion, reqVO.getRegion())
                .eqIfPresent(CmsQuotaDO::getIsFree, reqVO.getIsFree())
                .eqIfPresent(CmsQuotaDO::getAuditStatus, reqVO.getAuditStatus())
                .eqIfPresent(CmsQuotaDO::getPublishStatus, reqVO.getPublishStatus())
                .orderByDesc(CmsQuotaDO::getQuotaId));
    }

}
