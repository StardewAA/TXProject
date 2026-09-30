package cn.iocoder.txgy.module.training.service.basedata;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobDetailRespVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.JobDO;

import java.util.List;

/**
 * 岗位工种 Service（云培训学院基础数据字典）
 *
 * @author 云培训学院课题小组
 */
public interface JobService {

    Long createJob(JobSaveReqVO createReqVO);

    void updateJob(JobSaveReqVO updateReqVO);

    /**
     * 删除岗位工种（存在资格证或学习地图引用时返回 409 拦截）
     */
    void deleteJob(Long id);

    JobDO getJob(Long id);

    /**
     * 校验岗位工种存在（不存在抛 404）
     */
    JobDO validateJobExists(Long id);

    /**
     * 按编码校验岗位工种存在（不存在抛 404）
     */
    JobDO validateJobExistsByCode(String code);

    PageResult<JobDO> getJobPage(JobPageReqVO pageReqVO);

    /**
     * 全量工种列表（按序列 + 排序 + 编码升序，供下拉与学习地图工种选择）
     */
    List<JobDO> getJobList();

    /**
     * 工种全景详情：基础信息 + 资格证要求 + 学习地图（四阶段分组）
     *
     * @param code 工种编码
     */
    JobDetailRespVO getJobDetail(String code);

}
