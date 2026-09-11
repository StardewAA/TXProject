package cn.iocoder.txgy.module.training.service.analytics;

import java.util.Map;

/**
 * 96 外部系统对接 Service 接口
 *
 * @author 云培训学院课题小组
 */
public interface ExternalApiService {

    /**
     * 外部培训任务下发（岗前/复工培训）
     *
     * @param request 请求参数（projectId/userId/强制课程ID 等）
     * @return 任务ID与业务结果
     */
    Map<String, Object> dispatchTrainingTask(Map<String, Object> request);

    /**
     * 查询人员培训完成状态
     *
     * @param userIds 人员ID列表
     * @return 人员培训状态
     */
    Map<String, Object> getTrainStatus(java.util.List<Long> userIds);

    /**
     * 批量获取脱敏后的人员培训档案（单次最多 200 条）
     *
     * @param request 查询参数
     * @return 脱敏档案列表
     */
    Map<String, Object> getArchivesBatch(Map<String, Object> request);

}
