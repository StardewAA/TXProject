package cn.iocoder.txgy.module.training.dal.mysql.resource;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.DocumentPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.DocumentResourceDO;
import cn.iocoder.txgy.module.training.enums.resource.ResourceStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文档资源 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface DocumentResourceMapper extends BaseMapperX<DocumentResourceDO> {

    default PageResult<DocumentResourceDO> selectPage(DocumentPageReqVO reqVO) {
        LambdaQueryWrapperX<DocumentResourceDO> query = new LambdaQueryWrapperX<DocumentResourceDO>()
                .likeIfPresent(DocumentResourceDO::getTitle, reqVO.getKeyword())
                .eqIfPresent(DocumentResourceDO::getCategoryId, reqVO.getCategoryId())
                .eqIfPresent(DocumentResourceDO::getStatus, reqVO.getStatus())
                .orderByDesc(DocumentResourceDO::getId);
        // 默认不展示归档资源（includeArchived=true 时全量展示）
        if (Boolean.FALSE.equals(reqVO.getIncludeArchived())) {
            query.ne(DocumentResourceDO::getStatus, ResourceStatusEnum.ARCHIVED.getStatus());
        }
        return selectPage(reqVO, query);
    }

    default List<DocumentResourceDO> selectListByCategoryId(Long categoryId) {
        return selectList(new LambdaQueryWrapperX<DocumentResourceDO>()
                .eq(DocumentResourceDO::getCategoryId, categoryId));
    }

    default Long selectCountByStatus(Integer status) {
        return selectCount(new LambdaQueryWrapperX<DocumentResourceDO>()
                .eqIfPresent(DocumentResourceDO::getStatus, status));
    }

}
