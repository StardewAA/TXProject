package cn.iocoder.txgy.module.training.service.basedata;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobCertPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobCertSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.JobCertDO;

import java.util.List;

/**
 * 工种资格证 Service（持证上岗校验依据）
 *
 * @author 云培训学院课题小组
 */
public interface JobCertService {

    Long createJobCert(JobCertSaveReqVO createReqVO);

    void updateJobCert(JobCertSaveReqVO updateReqVO);

    void deleteJobCert(Long id);

    JobCertDO getJobCert(Long id);

    JobCertDO validateJobCertExists(Long id);

    PageResult<JobCertDO> getJobCertPage(JobCertPageReqVO pageReqVO);

    /**
     * 按工种查询资格证要求清单
     */
    List<JobCertDO> getJobCertListByJobCode(String jobCode);

    /**
     * 统计工种下的资格证数量
     */
    Long getJobCertCountByJobCode(String jobCode);

}
