package cn.iocoder.txgy.module.training.dal.mysql.plan;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassDO;
import cn.iocoder.txgy.module.training.enums.plan.ClassStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ClassMapper extends BaseMapperX<ClassDO> {

    default PageResult<ClassDO> selectPage(ClassPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ClassDO>()
                .likeIfPresent(ClassDO::getName, reqVO.getName())
                .likeIfPresent(ClassDO::getCode, reqVO.getCode())
                .eqIfPresent(ClassDO::getType, reqVO.getType())
                .eqIfPresent(ClassDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ClassDO::getProjectId, reqVO.getProjectId())
                .orderByDesc(ClassDO::getId));
    }

    default ClassDO selectByName(String name) {
        return selectOne(new LambdaQueryWrapperX<ClassDO>()
                .eq(ClassDO::getName, name)
                .last("limit 1"));
    }

    /**
     * 查询某项目下的岗前/复工类培训班（上岗资格核查使用）
     */
    default List<ClassDO> selectListByProjectAndTypes(String projectId, Collection<Integer> types) {
        return selectList(new LambdaQueryWrapperX<ClassDO>()
                .eq(ClassDO::getProjectId, projectId)
                .in(ClassDO::getType, types)
                .ne(ClassDO::getStatus, ClassStatusEnum.CANCELED.getStatus())
                .orderByDesc(ClassDO::getId));
    }

}
