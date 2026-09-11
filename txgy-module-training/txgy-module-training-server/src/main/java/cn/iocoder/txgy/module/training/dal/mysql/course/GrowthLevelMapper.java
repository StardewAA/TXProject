package cn.iocoder.txgy.module.training.dal.mysql.course;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.GrowthLevelPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.GrowthLevelDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 成长等级 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface GrowthLevelMapper extends BaseMapperX<GrowthLevelDO> {

    default PageResult<GrowthLevelDO> selectPage(GrowthLevelPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<GrowthLevelDO>()
                .likeIfPresent(GrowthLevelDO::getName, reqVO.getName())
                .eqIfPresent(GrowthLevelDO::getCode, reqVO.getCode())
                .eqIfPresent(GrowthLevelDO::getLevelNo, reqVO.getLevelNo())
                .orderByAsc(GrowthLevelDO::getLevelNo)
                .orderByAsc(GrowthLevelDO::getSort)
                .orderByAsc(GrowthLevelDO::getId));
    }

    /**
     * 全量等级列表（课程成长等级下拉 / 人才成长统计引用），按等级序号升序
     */
    default java.util.List<GrowthLevelDO> selectList() {
        return selectList(new LambdaQueryWrapperX<GrowthLevelDO>()
                .orderByAsc(GrowthLevelDO::getLevelNo)
                .orderByAsc(GrowthLevelDO::getSort)
                .orderByAsc(GrowthLevelDO::getId));
    }

}
