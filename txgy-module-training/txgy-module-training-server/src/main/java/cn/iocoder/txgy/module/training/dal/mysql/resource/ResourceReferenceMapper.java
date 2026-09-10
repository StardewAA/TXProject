package cn.iocoder.txgy.module.training.dal.mysql.resource;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.ResourceReferenceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资源引用 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface ResourceReferenceMapper extends BaseMapperX<ResourceReferenceDO> {

    default Long selectCountByResource(String resourceType, Long resourceId) {
        return selectCount(new LambdaQueryWrapperX<ResourceReferenceDO>()
                .eq(ResourceReferenceDO::getResourceType, resourceType)
                .eq(ResourceReferenceDO::getResourceId, resourceId));
    }

    default Long selectCountByRef(String refType, Long refId) {
        return selectCount(new LambdaQueryWrapperX<ResourceReferenceDO>()
                .eq(ResourceReferenceDO::getRefType, refType)
                .eq(ResourceReferenceDO::getRefId, refId));
    }

}
