package cn.iocoder.txgy.module.training.service.basedata;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.LearningMapPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.LearningMapSaveReqVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.LearningMapStageVO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.LearningMapDO;

import java.util.List;

/**
 * 学习地图 Service（按成长阶段组织的课程路径）
 *
 * @author 云培训学院课题小组
 */
public interface LearningMapService {

    Long createLearningMap(LearningMapSaveReqVO createReqVO);

    void updateLearningMap(LearningMapSaveReqVO updateReqVO);

    void deleteLearningMap(Long id);

    LearningMapDO getLearningMap(Long id);

    LearningMapDO validateLearningMapExists(Long id);

    PageResult<LearningMapDO> getLearningMapPage(LearningMapPageReqVO pageReqVO);

    /**
     * 按工种查询学习地图课程清单（阶段序号 + 阶段内排序）
     */
    List<LearningMapDO> getLearningMapListByJobCode(String jobCode);

    /**
     * 统计工种下的学习地图课程数量
     */
    Long getLearningMapCountByJobCode(String jobCode);

    /**
     * 按工种输出学习地图四阶段分组视图（入门段/核心段/进阶段/技师段，空阶段保留）
     *
     * @param jobCode 工种编码
     */
    List<LearningMapStageVO> getLearningMapStagesByJobCode(String jobCode);

}
