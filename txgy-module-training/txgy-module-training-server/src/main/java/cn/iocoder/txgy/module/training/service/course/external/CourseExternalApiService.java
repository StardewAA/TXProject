package cn.iocoder.txgy.module.training.service.course.external;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.training.config.ExamServiceProperties;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseLearningRefMapper;
import cn.iocoder.txgy.module.training.enums.course.CourseMediaTypeEnum;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.COURSE_MEDIA_REF_REQUIRED;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.COURSE_MEDIA_URL_REQUIRED;

/**
 * 课程对外对接服务
 *
 * <p>集中承载"课程管理与其他子模块的对接边界"：</p>
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

    /**
     * 平台租户头（模块三考试服务按租户维度隔离数据）
     */
    private static final String HEADER_TENANT_ID = "tenant-id";
    private static final String HEADER_AUTHORIZATION = "Authorization";

    @Resource
    private CourseLearningRefMapper courseLearningRefMapper;

    @Resource
    private ExamServiceProperties examServiceProperties;

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
        // 资源中心（模块一）：携带 mediaRefId 时视为引用资源中心既有素材，URL 可为空（由服务端补齐预览地址）
        if (StrUtil.isNotBlank(mediaRefId)) {
            return;
        }
        // 未携带引用 ID 时按自含媒资处理，必须提供访问地址
        if (StrUtil.isBlank(url)) {
            throw exception(COURSE_MEDIA_URL_REQUIRED);
        }
    }

    /**
     * 结课考试试卷下拉：读取模块三「考试与认证」已建试卷
     *
     * <p>采用透传当前登录用户 token 的方式调用模块三接口，保证租户数据隔离与权限一致；
     * 模块三不可用或当前用户无权读取时降级为空列表并记录告警，不阻断课程编辑。</p>
     *
     * @return [{id, name, status, examType, totalScore, passScore, durationMin, questionCount, courseName, courseId}]
     */
    public List<Map<String, Object>> listExamOptions() {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!Boolean.TRUE.equals(examServiceProperties.getEnabled())) {
            return result;
        }
        JSONArray papers = requestExamPapers();
        if (papers == null) {
            return result;
        }
        for (Object item : papers) {
            if (!(item instanceof JSONObject paper)) {
                continue;
            }
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("id", paper.getLong("id"));
            option.put("name", paper.getStr("name"));
            option.put("status", paper.getStr("status"));
            option.put("examType", paper.getStr("exam_type"));
            option.put("totalScore", paper.get("total_score"));
            option.put("passScore", paper.get("pass_score"));
            option.put("durationMin", paper.get("duration_min"));
            option.put("questionCount", paper.get("question_count"));
            option.put("courseName", paper.getStr("course_name"));
            option.put("courseId", paper.get("course_id"));
            result.add(option);
        }
        return result;
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

    // ========== 模块三「考试与认证」调用 ==========

    /**
     * 请求模块三试卷列表，失败时返回 null（降级）
     */
    private JSONArray requestExamPapers() {
        String url = StrUtil.removeSuffix(examServiceProperties.getBaseUrl(), "/") + "/api/papers";
        JSONObject body = executeExamRequest(url);
        if (body == null) {
            return null;
        }
        return body.getJSONArray("data");
    }

    /**
     * 执行模块三请求并解析统一响应体 {code, message, data}
     */
    private JSONObject executeExamRequest(String url) {
        String authorization = currentAuthorization();
        if (StrUtil.isBlank(authorization)) {
            log.warn("[executeExamRequest][跳过] 当前请求缺少 Authorization，无法读取模块三数据：{}", url);
            return null;
        }
        try {
            String response = HttpRequest.get(url)
                    .header(HEADER_AUTHORIZATION, authorization)
                    .header(HEADER_TENANT_ID, String.valueOf(TenantContextHolder.getTenantId()))
                    .timeout(examServiceProperties.getTimeoutMs())
                    .execute().body();
            if (StrUtil.isBlank(response)) {
                log.warn("[executeExamRequest][响应为空] {}", url);
                return null;
            }
            return JSONUtil.parseObj(response);
        } catch (Exception e) {
            log.warn("[executeExamRequest][模块三不可用] url={} error={}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前请求的 Authorization（用于向模块三透传登录态）
     */
    private String currentAuthorization() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request == null ? null : request.getHeader(HEADER_AUTHORIZATION);
    }

}
