package cn.iocoder.txgy.module.training.service.basedata;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.LearningMapPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.LearningMapRespVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.LearningMapSaveReqVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.LearningMapStageVO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.JobDO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.LearningMapDO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseCategoryDO;
import cn.iocoder.txgy.module.training.dal.mysql.basedata.JobMapper;
import cn.iocoder.txgy.module.training.dal.mysql.basedata.LearningMapMapper;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseCategoryMapper;
import cn.iocoder.txgy.module.training.enums.basedata.LearningStageEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.*;

/**
 * 学习地图 Service 实现类
 *
 * @author 云培训学院课题小组
 */
@Service
@Validated
public class LearningMapServiceImpl implements LearningMapService {

    /**
     * 课程分类中「课程级」的层级
     */
    private static final Integer CATEGORY_LEVEL_COURSE = 3;

    @Resource
    private LearningMapMapper learningMapMapper;

    @Resource
    private JobMapper jobMapper;

    @Resource
    private CourseCategoryMapper courseCategoryMapper;

    @Override
    public Long createLearningMap(LearningMapSaveReqVO createReqVO) {
        JobDO job = validateJobExists(createReqVO.getJobCode());
        LearningStageEnum stage = validateStage(createReqVO.getStage());
        CourseCategoryDO course = validateCourseExists(createReqVO.getCourseCode());
        validateLearningMapUnique(null, createReqVO.getJobCode(), stage.getStage(), createReqVO.getCourseCode());
        LearningMapDO map = BeanUtils.toBean(createReqVO, LearningMapDO.class);
        map.setJobName(job.getName());
        map.setStage(stage.getStage());
        map.setStageName(stage.getName());
        map.setStageNo(stage.getNo());
        if (StrUtil.isBlank(map.getCourseName())) {
            map.setCourseName(course.getName());
        }
        if (map.getIsRequired() == null) {
            map.setIsRequired(Boolean.TRUE);
        }
        learningMapMapper.insert(map);
        return map.getId();
    }

    @Override
    public void updateLearningMap(LearningMapSaveReqVO updateReqVO) {
        validateLearningMapExists(updateReqVO.getId());
        JobDO job = validateJobExists(updateReqVO.getJobCode());
        LearningStageEnum stage = validateStage(updateReqVO.getStage());
        CourseCategoryDO course = validateCourseExists(updateReqVO.getCourseCode());
        validateLearningMapUnique(updateReqVO.getId(), updateReqVO.getJobCode(), stage.getStage(),
                updateReqVO.getCourseCode());
        LearningMapDO updateObj = BeanUtils.toBean(updateReqVO, LearningMapDO.class);
        updateObj.setJobName(job.getName());
        updateObj.setStage(stage.getStage());
        updateObj.setStageName(stage.getName());
        updateObj.setStageNo(stage.getNo());
        if (StrUtil.isBlank(updateObj.getCourseName())) {
            updateObj.setCourseName(course.getName());
        }
        learningMapMapper.updateById(updateObj);
    }

    @Override
    public void deleteLearningMap(Long id) {
        validateLearningMapExists(id);
        learningMapMapper.deleteById(id);
    }

    @Override
    public LearningMapDO getLearningMap(Long id) {
        return learningMapMapper.selectById(id);
    }

    @Override
    public LearningMapDO validateLearningMapExists(Long id) {
        if (id == null) {
            throw exception(LEARNING_MAP_NOT_EXISTS);
        }
        LearningMapDO map = learningMapMapper.selectById(id);
        if (map == null) {
            throw exception(LEARNING_MAP_NOT_EXISTS);
        }
        return map;
    }

    @Override
    public PageResult<LearningMapDO> getLearningMapPage(LearningMapPageReqVO pageReqVO) {
        return learningMapMapper.selectPage(pageReqVO);
    }

    @Override
    public List<LearningMapDO> getLearningMapListByJobCode(String jobCode) {
        return learningMapMapper.selectListByJobCode(jobCode);
    }

    @Override
    public Long getLearningMapCountByJobCode(String jobCode) {
        return learningMapMapper.selectCountByJobCode(jobCode);
    }

    @Override
    public List<LearningMapStageVO> getLearningMapStagesByJobCode(String jobCode) {
        List<LearningMapDO> list = learningMapMapper.selectListByJobCode(jobCode);
        Map<String, List<LearningMapDO>> grouped = list.stream()
                .collect(Collectors.groupingBy(LearningMapDO::getStage));
        List<LearningMapStageVO> stages = new ArrayList<>(LearningStageEnum.values().length);
        for (LearningStageEnum stageEnum : LearningStageEnum.values()) {
            LearningMapStageVO stageVO = new LearningMapStageVO();
            stageVO.setStage(stageEnum.getStage());
            stageVO.setStageName(stageEnum.getName());
            stageVO.setStageNo(stageEnum.getNo());
            List<LearningMapRespVO> courses = BeanUtils.toBean(
                    grouped.getOrDefault(stageEnum.getStage(), Collections.emptyList()),
                    LearningMapRespVO.class);
            stageVO.setCourses(courses);
            stageVO.setCourseCount(courses.size());
            stages.add(stageVO);
        }
        return stages;
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

    private LearningStageEnum validateStage(String stage) {
        LearningStageEnum stageEnum = LearningStageEnum.of(stage);
        if (stageEnum == null) {
            throw exception(LEARNING_MAP_STAGE_INVALID);
        }
        return stageEnum;
    }

    /**
     * 校验课程编码必须存在于课程分类的「课程级」（L3），保证学习地图与课程分类的关联有效性
     */
    private CourseCategoryDO validateCourseExists(String courseCode) {
        CourseCategoryDO course = courseCategoryMapper.selectOne(new LambdaQueryWrapperX<CourseCategoryDO>()
                .eq(CourseCategoryDO::getCode, courseCode)
                .eq(CourseCategoryDO::getLevel, CATEGORY_LEVEL_COURSE)
                .last("limit 1"));
        if (course == null) {
            throw exception(LEARNING_MAP_COURSE_NOT_EXISTS);
        }
        return course;
    }

    private void validateLearningMapUnique(Long id, String jobCode, String stage, String courseCode) {
        List<LearningMapDO> list = learningMapMapper.selectListByJobCode(jobCode);
        boolean exists = list.stream()
                .anyMatch(map -> Objects.equals(map.getStage(), stage)
                        && Objects.equals(map.getCourseCode(), courseCode)
                        && (id == null || !Objects.equals(map.getId(), id)));
        if (exists) {
            throw exception(LEARNING_MAP_EXISTS);
        }
    }

}
