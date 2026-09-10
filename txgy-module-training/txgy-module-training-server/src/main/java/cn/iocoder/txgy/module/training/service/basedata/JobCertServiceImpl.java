package cn.iocoder.txgy.module.training.service.basedata;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobCertPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobCertSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.JobCertDO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.JobDO;
import cn.iocoder.txgy.module.training.dal.mysql.basedata.JobCertMapper;
import cn.iocoder.txgy.module.training.dal.mysql.basedata.JobMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.*;

/**
 * 工种资格证 Service 实现类
 *
 * @author 云培训学院课题小组
 */
@Service
@Validated
public class JobCertServiceImpl implements JobCertService {

    @Resource
    private JobCertMapper jobCertMapper;

    @Resource
    private JobMapper jobMapper;

    @Override
    public Long createJobCert(JobCertSaveReqVO createReqVO) {
        JobDO job = validateJobExists(createReqVO.getJobCode());
        validateJobCertUnique(null, createReqVO.getJobCode(), createReqVO.getCertName());
        JobCertDO cert = BeanUtils.toBean(createReqVO, JobCertDO.class);
        cert.setJobName(job.getName());
        if (cert.getIsRequired() == null) {
            cert.setIsRequired(Boolean.TRUE);
        }
        jobCertMapper.insert(cert);
        return cert.getId();
    }

    @Override
    public void updateJobCert(JobCertSaveReqVO updateReqVO) {
        validateJobCertExists(updateReqVO.getId());
        JobDO job = validateJobExists(updateReqVO.getJobCode());
        validateJobCertUnique(updateReqVO.getId(), updateReqVO.getJobCode(), updateReqVO.getCertName());
        JobCertDO updateObj = BeanUtils.toBean(updateReqVO, JobCertDO.class);
        updateObj.setJobName(job.getName());
        jobCertMapper.updateById(updateObj);
    }

    @Override
    public void deleteJobCert(Long id) {
        validateJobCertExists(id);
        jobCertMapper.deleteById(id);
    }

    @Override
    public JobCertDO getJobCert(Long id) {
        return jobCertMapper.selectById(id);
    }

    @Override
    public JobCertDO validateJobCertExists(Long id) {
        if (id == null) {
            throw exception(JOB_CERT_NOT_EXISTS);
        }
        JobCertDO cert = jobCertMapper.selectById(id);
        if (cert == null) {
            throw exception(JOB_CERT_NOT_EXISTS);
        }
        return cert;
    }

    @Override
    public PageResult<JobCertDO> getJobCertPage(JobCertPageReqVO pageReqVO) {
        return jobCertMapper.selectPage(pageReqVO);
    }

    @Override
    public List<JobCertDO> getJobCertListByJobCode(String jobCode) {
        return jobCertMapper.selectListByJobCode(jobCode);
    }

    @Override
    public Long getJobCertCountByJobCode(String jobCode) {
        return jobCertMapper.selectCountByJobCode(jobCode);
    }

    private JobDO validateJobExists(String jobCode) {
        if (StrUtil.isBlank(jobCode)) {
            throw exception(JOB_NOT_EXISTS);
        }
        JobDO job = jobMapper.selectByCode(jobCode);
        if (job == null) {
            throw exception(JOB_NOT_EXISTS);
        }
        return job;
    }

    private void validateJobCertUnique(Long id, String jobCode, String certName) {
        if (StrUtil.isBlank(jobCode) || StrUtil.isBlank(certName)) {
            return;
        }
        JobCertDO cert = jobCertMapper.selectByJobCodeAndCertName(jobCode, certName);
        if (cert == null) {
            return;
        }
        if (id == null || !Objects.equals(cert.getId(), id)) {
            throw exception(JOB_CERT_EXISTS);
        }
    }

}
