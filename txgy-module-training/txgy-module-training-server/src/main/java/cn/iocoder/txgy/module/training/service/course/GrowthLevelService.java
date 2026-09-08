package cn.iocoder.txgy.module.training.service.course;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.GrowthLevelPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.GrowthLevelSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.GrowthLevelDO;

import java.util.List;

/**
 * 成长等级 Service（六级成长体系字典：课程等级标记/人才成长统计引用）
 *
 * @author 云培训学院课题小组
 */
public interface GrowthLevelService {

    Long createGrowthLevel(GrowthLevelSaveReqVO createReqVO);

    void updateGrowthLevel(GrowthLevelSaveReqVO updateReqVO);

    /**
     * 删除成长等级（已被课程标记引用时返回 409 拦截）
     */
    void deleteGrowthLevel(Long id);

    PageResult<GrowthLevelDO> getGrowthLevelPage(GrowthLevelPageReqVO pageReqVO);

    /**
     * 全量列表（按等级序号升序，供课程成长等级下拉等引用）
     */
    List<GrowthLevelDO> getGrowthLevelList();

    GrowthLevelDO getGrowthLevel(Long id);

}
