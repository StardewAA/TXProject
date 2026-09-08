package cn.iocoder.txgy.module.training.service.course.external;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseLearningRefMapper;
import cn.iocoder.txgy.module.training.enums.course.CourseMediaTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.COURSE_MEDIA_REF_REQUIRED;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.COURSE_MEDIA_URL_REQUIRED;

/**
 * 课程对外对接占位服务
 *
 * <p>集中承载"课程管理与其他子模块的对接边界"，当前均为占位实现，
 * 后续各子模块交付后只需替换本类方法实现（无需改动课程主流程）：</p>
 * <ul>
 *   <li>资源中心（模块一）：媒资选择/存在性校验 → {@link #validateMediaReference}</li>
 *   <li>考试中心（模块三）：结课考试试卷下拉 → {@link #listExamOptions}</li>
 *   <li>VR 平台（模块七）：VR 项目校验 → {@link #validateMediaReference}</li>
 *   <li>学员学习记录（模块五）：删除保护引用 → {@link #hasLearningRef}</li>
 * </ul>
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class CourseExternalApiService {

    @Resource
    private CourseLearningRefMapper courseLearningRefMapper;

    /**
     * 校验媒资外部引用有效性（占位实现）
     *
     * @param mediaType  媒资类型 {@link CourseMediaTypeEnum}
     * @param url        访问地址（文档/视频/图片 自含媒资）
     * @param mediaRefId 外部资源引用 ID（VR 项目编号 / 资源中心媒资 ID）
     */
    public void validateMediaReference(Integer mediaType, String url, String mediaRefId) {
        boolean vr = CourseMediaTypeEnum.VR.getType().equals(mediaType);
        if (vr) {
            // TODO(vr-platform)：正式对接模块七 VR 平台，校验项目编号存在且可用
            if (StrUtil.isBlank(mediaRefId)) {
                throw exception(COURSE_MEDIA_REF_REQUIRED);
            }
            log.info("[validateMediaReference][占位] VR 项目编号 {} 校验通过，待对接 VR 平台", mediaRefId);
            return;
        }
        // TODO(resource-center)：正式对接模块一资源中心后，改为按 mediaRefId 校验媒资存在；
        //  当前占位阶段允许直接录入 URL（自含媒资）并可携带 mediaRefId 预留
        if (StrUtil.isBlank(url)) {
            throw exception(COURSE_MEDIA_URL_REQUIRED);
        }
        if (StrUtil.isNotBlank(mediaRefId)) {
            log.info("[validateMediaReference][占位] 外部媒资引用 {} 校验通过，待对接资源中心", mediaRefId);
        }
    }

    /**
     * 结课考试试卷下拉（占位：模块三试卷中心未接入，返回空列表）
     * 前端展示空态提示即可；正式接入后返回 [{id, name}] 列表。
     */
    public List<Map<String, Object>> listExamOptions() {
        // TODO(exam-center)：对接模块三试卷中心 GET /admin-api/exam/paper/list
        return new ArrayList<>();
    }

    /**
     * 课程是否已被学习引用（删除保护）
     */
    public boolean hasLearningRef(Long courseId) {
        return courseLearningRefMapper.selectCountByCourseId(courseId) > 0;
    }

    /**
     * 登记学习引用占位记录（模拟学员端已学习该课程）
     */
    public void addLearningRef(Long courseId, Long userId, String userName) {
        // TODO(module-5)：正式由学员端学习记录写入，此处仅演示
        cn.iocoder.txgy.module.training.dal.dataobject.course.CourseLearningRefDO ref =
                new cn.iocoder.txgy.module.training.dal.dataobject.course.CourseLearningRefDO();
        ref.setCourseId(courseId);
        ref.setUserId(userId);
        ref.setUserName(userName);
        ref.setRefType("LEARN");
        ref.setRemark("学员端学习记录占位登记");
        courseLearningRefMapper.insert(ref);
    }

}
