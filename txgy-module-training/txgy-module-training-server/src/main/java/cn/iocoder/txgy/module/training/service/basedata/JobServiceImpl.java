package cn.iocoder.txgy.module.training.service.basedata;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobCertRespVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobDetailRespVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobRespVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.JobDO;
import cn.iocoder.txgy.module.training.dal.mysql.basedata.JobMapper;
import cn.iocoder.txgy.module.training.service.course.GrowthLevelService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.*;

/**
 * 岗位工种 Service 实现类
 *
 * @author 云培训学院课题小组
 */
@Service
@Validated
public class JobServiceImpl implements JobService {

    /**
     * 默认启用状态
     */
    private static final String STATUS_ACTIVE = "active";

    @Resource
    private JobMapper jobMapper;

    @Resource
    private JobCertService jobCertService;

    @Resource
    private LearningMapService learningMapService;

    @Resource
    private GrowthLevelService growthLevelService;

    @Override
    public Long createJob(JobSaveReqVO createReqVO) {
        validateJobCodeUnique(null, createReqVO.getCode());
        validateJobNameUnique(null, createReqVO.getName());
        JobDO job = BeanUtils.toBean(createReqVO, JobDO.class);
        if (StrUtil.isBlank(job.getStatus())) {
            job.setStatus(STATUS_ACTIVE);
        }
        jobMapper.insert(job);
        return job.getId();
    }

    @Override
    public void updateJob(JobSaveReqVO updateReqVO) {
        validateJobExists(updateReqVO.getId());
        validateJobCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        validateJobNameUnique(updateReqVO.getId(), updateReqVO.getName());
        JobDO updateObj = BeanUtils.toBean(updateReqVO, JobDO.class);
        jobMapper.updateById(updateObj);
    }

    @Override
    public void deleteJob(Long id) {
        JobDO job = validateJobExists(id);
        // 关联性保护：工种被资格证 / 学习地图引用时禁止删除
        if (jobCertService.getJobCertCountByJobCode(job.getCode()) > 0) {
            throw exception(JOB_HAS_CERT);
        }
        if (learningMapService.getLearningMapCountByJobCode(job.getCode()) > 0) {
            throw exception(JOB_HAS_LEARNING_MAP);
        }
        jobMapper.deleteById(id);
    }

    @Override
    public JobDO getJob(Long id) {
        return jobMapper.selectById(id);
    }

    @Override
    public JobDO validateJobExists(Long id) {
        if (id == null) {
            throw exception(JOB_NOT_EXISTS);
        }
        JobDO job = jobMapper.selectById(id);
        if (job == null) {
            throw exception(JOB_NOT_EXISTS);
        }
        return job;
    }

    @Override
    public JobDO validateJobExistsByCode(String code) {
        JobDO job = jobMapper.selectByCode(code);
        if (job == null) {
            throw exception(JOB_NOT_EXISTS);
        }
        return job;
    }

    @Override
    public PageResult<JobDO> getJobPage(JobPageReqVO pageReqVO) {
        return jobMapper.selectPage(pageReqVO);
    }

    @Override
    public List<JobDO> getJobList() {
        return jobMapper.selectList();
    }

    @Override
    public JobDetailRespVO getJobDetail(String code) {
        JobDO job = validateJobExistsByCode(code);
        JobDetailRespVO detail = new JobDetailRespVO();
        // 1. 工种基础信息（翻译最高成长等级名称）
        JobRespVO jobRespVO = BeanUtils.toBean(job, JobRespVO.class);
        if (StrUtil.isNotBlank(job.getMaxLevel())) {
            growthLevelService.getGrowthLevelList().stream()
                    .filter(level -> Objects.equals(level.getCode(), job.getMaxLevel()))
                    .findFirst()
                    .ifPresent(level -> jobRespVO.setMaxLevelName(level.getName()));
        }
        jobRespVO.setCertCount(jobCertService.getJobCertListByJobCode(code).size());
        jobRespVO.setMapCourseCount(learningMapService.getLearningMapListByJobCode(code).size());
        detail.setJob(jobRespVO);
        // 2. 资格证要求
        detail.setCerts(BeanUtils.toBean(jobCertService.getJobCertListByJobCode(code), JobCertRespVO.class));
        // 3. 学习地图（四阶段分组）
        detail.setStages(learningMapService.getLearningMapStagesByJobCode(code));
        return detail;
    }

    private void validateJobCodeUnique(Long id, String code) {
        if (StrUtil.isBlank(code)) {
            return;
        }
        JobDO job = jobMapper.selectByCode(code);
        if (job == null) {
            return;
        }
        if (id == null || !Objects.equals(job.getId(), id)) {
            throw exception(JOB_CODE_EXISTS);
        }
    }

    private void validateJobNameUnique(Long id, String name) {
        if (StrUtil.isBlank(name)) {
            return;
        }
        List<JobDO> list = jobMapper.selectList();
        boolean exists = list.stream()
                .anyMatch(job -> Objects.equals(job.getName(), name)
                        && (id == null || !Objects.equals(job.getId(), id)));
        if (exists) {
            throw exception(JOB_NAME_EXISTS);
        }
    }

}
