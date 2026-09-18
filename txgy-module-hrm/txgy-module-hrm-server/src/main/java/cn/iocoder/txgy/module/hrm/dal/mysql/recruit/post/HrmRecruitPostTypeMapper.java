package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.post;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostTypeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface HrmRecruitPostTypeMapper extends BaseMapperX<HrmRecruitPostTypeDO> {

    default List<HrmRecruitPostTypeDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitPostTypeDO>()
                .eqIfPresent(HrmRecruitPostTypeDO::getStatus, status)
                .orderByAsc(HrmRecruitPostTypeDO::getSort)
                .orderByAsc(HrmRecruitPostTypeDO::getId));
    }

    default List<Long> selectIdsByName(String name) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitPostTypeDO>()
                .eq(HrmRecruitPostTypeDO::getName, name)
                .eq(HrmRecruitPostTypeDO::getStatus, 0))
                .stream().map(HrmRecruitPostTypeDO::getId).toList();
    }

    default HrmRecruitPostTypeDO selectByNameAndParentId(String name, Long parentId) {
        return selectOne(new LambdaQueryWrapperX<HrmRecruitPostTypeDO>()
                .eq(HrmRecruitPostTypeDO::getName, name)
                .eq(HrmRecruitPostTypeDO::getParentId, parentId)
                .orderByAsc(HrmRecruitPostTypeDO::getId)
                .last("LIMIT 1"));
    }

    default boolean hasChildren(Long parentId) {
        return selectCount(new LambdaQueryWrapperX<HrmRecruitPostTypeDO>()
                .eq(HrmRecruitPostTypeDO::getParentId, parentId)) > 0;
    }

    default List<HrmRecruitPostTypeDO> selectListByParentId(Collection<Long> parentIds) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitPostTypeDO>()
                .in(HrmRecruitPostTypeDO::getParentId, parentIds)
                .orderByAsc(HrmRecruitPostTypeDO::getSort)
                .orderByAsc(HrmRecruitPostTypeDO::getId));
    }

}
