package cn.iocoder.txgy.module.cms.dal.mysql.Material;

import cn.iocoder.txgy.module.cms.controller.admin.Material.vo.CmsMaterialPageReqVO;
import cn.iocoder.txgy.module.cms.dal.dataobject.Material.CmsMaterialDO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 材料参数数据库 Mapper
 *
 * @author yuting_feng
 */
@Mapper
public interface CmsMaterialMapper extends BaseMapperX<CmsMaterialDO> {

    default PageResult<CmsMaterialDO> selectPage(CmsMaterialPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CmsMaterialDO>()
                .likeIfPresent(CmsMaterialDO::getMaterialName, reqVO.getMaterialName())
                .likeIfPresent(CmsMaterialDO::getMaterialSubcategory, reqVO.getMaterialSubcategory())
                .eqIfPresent(CmsMaterialDO::getMaterialCategory, reqVO.getMaterialCategory())
                .eqIfPresent(CmsMaterialDO::getCombustionGrade, reqVO.getCombustionGrade())
                .eqIfPresent(CmsMaterialDO::getAuditStatus, reqVO.getAuditStatus())
                .eqIfPresent(CmsMaterialDO::getPublishStatus, reqVO.getPublishStatus())
                .orderByDesc(CmsMaterialDO::getMaterialId));
    }

}
