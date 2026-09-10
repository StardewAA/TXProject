package cn.iocoder.txgy.module.training.enums.resource;

import cn.hutool.core.util.StrUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 资源中心常量：分类类型、标签维度、动作、对象类型、上传白名单与大小上限
 */
public final class ResourceConstants {

    private ResourceConstants() {}

    // ========== 分类类型 ==========
    public static final String TYPE_DOC = "doc";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_QUESTION = "question";
    public static final Set<String> CATEGORY_TYPES = new HashSet<>(Arrays.asList(TYPE_DOC, TYPE_VIDEO, TYPE_QUESTION));

    // ========== 标签维度 ==========
    public static final String DIM_WORK_TYPE = "work_type"; // 工种
    public static final String DIM_COURSE = "course"; // 课程
    public static final String DIM_SAFETY = "safety"; // 安全
    public static final String DIM_PROCESS = "process"; // 工艺
    public static final Set<String> TAG_DIMENSIONS = new HashSet<>(
            Arrays.asList(DIM_WORK_TYPE, DIM_COURSE, DIM_SAFETY, DIM_PROCESS));

    // ========== 上传白名单 / 大小上限 ==========
    // 文档：PDF + Word(doc/docx) + WPS(wps/et/dps) + 图片（原有能力保留）
    public static final Set<String> DOC_ALLOWED_EXTS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "wps", "jpg", "jpeg", "png"));
    // 办公文档扩展名（Word / WPS），用于区分解析方式
    public static final Set<String> DOC_OFFICE_EXTS = new HashSet<>(Arrays.asList("doc", "docx", "wps"));
    // 视频：MP4（短视频 / 录播）+ 原有 mov
    public static final Set<String> VIDEO_ALLOWED_EXTS = new HashSet<>(Arrays.asList("mp4", "mov"));
    public static final long MAX_DOC_SIZE = 50L * 1024 * 1024; // 50MB
    public static final long MAX_VIDEO_SIZE = 4096L * 1024 * 1024; // 4GB（覆盖 1080P 长录播课程视频）
    public static final long MAX_AI_DOC_SIZE = 20L * 1024 * 1024; // AI 导入文档 20MB 上限

    // ========== 视频类型（短视频 / 录播）==========
    public static final Integer VIDEO_TYPE_SHORT = 1;
    public static final Integer VIDEO_TYPE_RECORD = 2;

    // ========== 清晰度（由高到低，弱网可向下切换）==========
    public static final String DEFINITION_2160P = "2160P";
    public static final String DEFINITION_1080P = "1080P";
    public static final String DEFINITION_720P = "720P";
    public static final String DEFINITION_480P = "480P";
    public static final String DEFINITION_360P = "360P";
    public static final Set<String> DEFINITIONS = new HashSet<>(Arrays.asList(
            DEFINITION_2160P, DEFINITION_1080P, DEFINITION_720P, DEFINITION_480P, DEFINITION_360P));

    /**
     * 清晰度由高到低的排序（用于生成降级播放项）
     */
    public static final java.util.List<String> DEFINITION_ORDER = Arrays.asList(
            DEFINITION_2160P, DEFINITION_1080P, DEFINITION_720P, DEFINITION_480P, DEFINITION_360P);

    // ========== 对象类型（target_type / resource_type）==========
    public static final String TARGET_DOCUMENT = "document";
    public static final String TARGET_VIDEO = "video";
    public static final String TARGET_QUESTION = "question";
    public static final String TARGET_CATEGORY = "category";
    public static final String TARGET_TAG = "tag";

    // ========== 操作动作（operation_log.action）==========
    public static final String ACTION_UPLOAD = "UPLOAD";
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_ONLINE = "ONLINE";
    public static final String ACTION_OFFLINE = "OFFLINE";
    public static final String ACTION_ARCHIVE = "ARCHIVE";
    public static final String ACTION_DRAFT = "DRAFT";
    public static final String ACTION_CREDIT = "CREDIT";
    public static final String ACTION_TRANSCODE = "TRANSCODE";
    public static final String ACTION_ENABLE = "ENABLE";
    public static final String ACTION_DISABLE = "DISABLE";
    public static final String ACTION_IMPORT = "IMPORT";
    public static final String ACTION_READING = "READING";

    /**
     * 获取文件扩展名（小写，不含点）；无扩展名返回空串
     */
    public static String getFileExt(String fileName) {
        if (StrUtil.isBlank(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
