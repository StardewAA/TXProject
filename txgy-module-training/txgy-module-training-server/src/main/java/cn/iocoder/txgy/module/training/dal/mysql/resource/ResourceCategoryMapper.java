package cn.iocoder.txgy.module.training.dal.mysql.resource;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.ResourceCategoryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 资源分类 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface ResourceCategoryMapper extends BaseMapperX<ResourceCategoryDO> {

    default List<ResourceCategoryDO> selectListByType(String type) {
        return selectList(new LambdaQueryWrapperX<ResourceCategoryDO>()
                .eq(ResourceCategoryDO::getType, type)
                .orderByAsc(ResourceCategoryDO::getParentId)
                .orderByAsc(ResourceCategoryDO::getSort)
                .orderByAsc(ResourceCategoryDO::getId));
    }

    default List<ResourceCategoryDO> selectListByParentId(Long parentId) {
        return selectList(new LambdaQueryWrapperX<ResourceCategoryDO>()
                .eq(ResourceCategoryDO::getParentId, parentId)
                .orderByAsc(ResourceCategoryDO::getSort));
    }

    default ResourceCategoryDO selectByNameAndType(String type, String name) {
        return selectOne(new LambdaQueryWrapperX<ResourceCategoryDO>()
                .eq(ResourceCategoryDO::getType, type)
                .eq(ResourceCategoryDO::getName, name)
                .last("limit 1"));
    }

    default ResourceCategoryDO selectByTypeAndCode(String type, String code) {
        return selectOne(new LambdaQueryWrapperX<ResourceCategoryDO>()
                .eq(ResourceCategoryDO::getType, type)
                .eq(ResourceCategoryDO::getCode, code)
                .last("limit 1"));
    }

}
