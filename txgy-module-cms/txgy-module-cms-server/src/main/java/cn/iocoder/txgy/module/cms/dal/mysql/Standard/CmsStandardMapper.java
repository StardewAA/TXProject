package cn.iocoder.txgy.module.cms.dal.mysql.Standard;

import cn.iocoder.txgy.module.cms.controller.admin.Standard.vo.CmsStandardPageReqVO;
import cn.iocoder.txgy.module.cms.dal.dataobject.Standard.CmsStandardDO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行业标准规范库 Mapper
 *
 * @author yuting_feng
 */
@Mapper
public interface CmsStandardMapper extends BaseMapperX<CmsStandardDO> {

    default PageResult<CmsStandardDO> selectPage(CmsStandardPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CmsStandardDO>()
                .likeIfPresent(CmsStandardDO::getStandardName, reqVO.getStandardName())
                .likeIfPresent(CmsStandardDO::getStandardNo, reqVO.getStandardNo())
                .likeIfPresent(CmsStandardDO::getKeywords, reqVO.getKeywords())
                .eqIfPresent(CmsStandardDO::getStandardType, reqVO.getStandardType())
                .eqIfPresent(CmsStandardDO::getStatus, reqVO.getStatus())
                .eqIfPresent(CmsStandardDO::getAuditStatus, reqVO.getAuditStatus())
                .eqIfPresent(CmsStandardDO::getPublishStatus, reqVO.getPublishStatus())
                .orderByDesc(CmsStandardDO::getStandardId));
    }

}
