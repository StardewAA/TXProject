package cn.iocoder.txgy.module.training.dal.mysql.resource;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.ResourceTagPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.ResourceTagDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 标签 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface ResourceTagMapper extends BaseMapperX<ResourceTagDO> {

    default PageResult<ResourceTagDO> selectPage(ResourceTagPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ResourceTagDO>()
                .likeIfPresent(ResourceTagDO::getName, reqVO.getName())
                .eqIfPresent(ResourceTagDO::getDimension, reqVO.getDimension())
                .orderByAsc(ResourceTagDO::getDimension)
                .orderByAsc(ResourceTagDO::getSort)
                .orderByDesc(ResourceTagDO::getId));
    }

    default List<ResourceTagDO> selectListByDimension(String dimension) {
        return selectList(new LambdaQueryWrapperX<ResourceTagDO>()
                .eqIfPresent(ResourceTagDO::getDimension, dimension)
                .orderByAsc(ResourceTagDO::getSort)
                .orderByAsc(ResourceTagDO::getId));
    }

    default ResourceTagDO selectByNameAndDimension(String name, String dimension) {
        return selectOne(new LambdaQueryWrapperX<ResourceTagDO>()
                .eq(ResourceTagDO::getName, name)
                .eq(ResourceTagDO::getDimension, dimension)
                .last("limit 1"));
    }

}
