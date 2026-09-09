package cn.iocoder.txgy.module.bidding.service.notice;
// 规范依据：《招投标模块开发规范》§2.1/2.2、§3.2；VO数据规范 数据集字段全集与必填=是；01/05 规则1（公开级别裁剪）

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeTypeEnum;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 公告载荷（payload）规整工具：保证"权威字段一个不能少"。
 *
 * <p>入参只允许出现对应数据集 VO（record）的组件字段，字段名照录规范英文名称（含拼写瑕疵）；
 * 输出为按数据集声明顺序排列的规范字段全集（缺失字段补 null，未知字段剔除），
 * 用于新建/修改前的校验与入库快照、详情返回的结构化形态。
 */
public final class NoticePayloadUtils {

    private NoticePayloadUtils() {
    }

    /**
     * 把业务侧提交的原始字段集规整为该公告类型对应数据集的规范字段全集。
     *
     * @param type 公告类型（决定权威数据集，见 {@link NoticeTypeEnum}）
     * @param raw  业务侧原始字段（key 用规范英文名称；value 统一转字符串，null 保留）
     * @return 按数据集声明顺序的规范字段全集（LinkedHashMap）
     * @throws IllegalArgumentException type 为空或非已登记类型时抛出
     */
    public static LinkedHashMap<String, Object> canonicalize(NoticeTypeEnum type, Map<String, Object> raw) {
        if (type == null) {
            throw new IllegalArgumentException("公告类型不能为空或未登记（NoticeTypeEnum）");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (RecordComponent component : type.getDatasetClass().getRecordComponents()) {
            String name = component.getName();
            Object value = raw == null ? null : raw.get(name);
            if (value == null) {
                result.put(name, null);
            } else if (value instanceof String string) {
                result.put(name, string);
            } else {
                result.put(name, String.valueOf(value));
            }
        }
        return result;
    }

    /**
     * 社会公开子集（权威规则：出参按调用方公开级别裁剪，见 05 接口设计文档规则 1）。
     *
     * <p>只保留数据集 VO 中标为 {@code @PublicLevel(SOCIAL)} 的组件；未标或政务公开一律不对外。
     *
     * @param type      公告类型（决定权威数据集）
     * @param canonical 规整后的字段全集（见 {@link #canonicalize}）
     * @return 社会公开字段子集（顺序照数据集声明）
     */
    public static LinkedHashMap<String, Object> socialSubset(NoticeTypeEnum type,
                                                             LinkedHashMap<String, Object> canonical) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (type == null || canonical == null) {
            return result;
        }
        for (RecordComponent component : type.getDatasetClass().getRecordComponents()) {
            PublicLevel publicLevel = component.getAnnotation(PublicLevel.class);
            if (publicLevel != null && publicLevel.value() == PublicLevelEnum.SOCIAL
                    && canonical.containsKey(component.getName())) {
                result.put(component.getName(), canonical.get(component.getName()));
            }
        }
        return result;
    }

    /** 权威字段中文字典（code→展示名；覆盖 REQUIRED_BY_TYPE 全部必填键，新增数据集/必填须同步登记）。前端类型表单模板据此渲染 */
    private static final java.util.Map<String, String> FIELD_LABELS = new java.util.HashMap<>();
    static {
        FIELD_LABELS.put("TENDER_PROJECT_CODE", "招标项目编号");
        FIELD_LABELS.put("NOTICE_BID_NUM", "标段（包）编号");
        FIELD_LABELS.put("BID_SECTION_CODES", "标段（包）编号集合");
        FIELD_LABELS.put("BID_SECTION_CODE", "标段（包）编号（单）");
        FIELD_LABELS.put("UNIFIED_DEAL_CODES", "统一交易编码（集合）");
        FIELD_LABELS.put("UNIFIED_DEAL_CODE", "统一交易编码");
        FIELD_LABELS.put("NOTICE_NAME", "公告/公示名称");
        FIELD_LABELS.put("NOTICE_CONTENT", "公告正文内容");
        FIELD_LABELS.put("NOTICE_MEDIA", "公告发布媒介");
        FIELD_LABELS.put("URL", "公告链接");
        FIELD_LABELS.put("NOTICE_NATURE", "公告性质");
        FIELD_LABELS.put("BULLETIN_TYPE", "公告类型代码");
        FIELD_LABELS.put("NOMINAL_FEE", "文件费用（元）");
        FIELD_LABELS.put("FILE_RECEIVE_ADDRESS", "文件获取地点/方式说明");
        FIELD_LABELS.put("DOC_GET_START_TIME", "文件获取开始时间");
        FIELD_LABELS.put("DOC_GET_END_TIME", "文件获取截止时间");
        FIELD_LABELS.put("TENDER_DOC_GET_METHOD", "招标文件获取方式");
        FIELD_LABELS.put("BID_DOC_REFER_END_TIME", "投标文件递交截止时间");
        FIELD_LABELS.put("BID_DOC_REFER_METHOD", "投标文件递交方式");
        FIELD_LABELS.put("BID_END_TIME", "投标截止时间");
        FIELD_LABELS.put("SYNDICATED_FLAG", "是否接受联合体投标");
        FIELD_LABELS.put("BULLETIN_DUTY", "公告发布责任人");
        FIELD_LABELS.put("BULLETIN_DUTY_CODE_TYPE", "责任人证件类型");
        FIELD_LABELS.put("FROM_TRADE_PLAT_CODE", "来源平台代码");
        FIELD_LABELS.put("ATTACHMENT_CODE", "附件编码");
        FIELD_LABELS.put("SUBMIT_TIMESTAMP", "提交时间戳");
        FIELD_LABELS.put("PLATFORM_CODE", "平台代码");
        FIELD_LABELS.put("PUB_SERVICE_PLAT_CODE", "公共资源交易平台代码");
        FIELD_LABELS.put("PUB_SERVICE_PLAT", "公共资源交易平台名称");
        FIELD_LABELS.put("TRADE_PLAT", "交易平台");
        FIELD_LABELS.put("DATA_TIMESTAMP", "数据时间戳");
        FIELD_LABELS.put("CHANGE_CONTENT", "变更内容");
        FIELD_LABELS.put("PUBLICITY_NAME", "公示名称");
        FIELD_LABELS.put("PUBLICITY_CONTENT", "公示内容");
        FIELD_LABELS.put("PUBLICITY_TYPE", "公示类型");
        FIELD_LABELS.put("PUBLICITY_START_TIME", "公示开始时间");
        FIELD_LABELS.put("PUBLICITY_END_TIME", "公示结束时间");
        FIELD_LABELS.put("IS_CHANGE", "是否变更");
        FIELD_LABELS.put("RECOMMEND_RANKING_FIRST", "第一中标候选人名称");
        FIELD_LABELS.put("RECOMMEND_RANKING_FIRST_LEGAL_CODE", "第一候选人统一信用代码");
        FIELD_LABELS.put("RECOMMEND_RANKING_FIRST_LEGAL_PM_NAME", "第一候选人项目负责人");
        FIELD_LABELS.put("RECOMMEND_RANKING_FIRST_WIN_PRICE", "第一候选人中标价（元）");
        FIELD_LABELS.put("RECOMMEND_RANKING_SECOND_LEGAL_PM_NAME", "第二候选人项目负责人");
        FIELD_LABELS.put("RECOMMEND_RANKING_SECOND_WIN_PRICE", "第二候选人中标价（元）");
        FIELD_LABELS.put("RECOMMEND_RANKING_THIRED_EGAL_PM_NAME", "第三候选人项目负责人");
        FIELD_LABELS.put("RECOMMEND_RANKING_THIRED_WIN_PRICE", "第三候选人中标价（元）");
        FIELD_LABELS.put("WIN_BIDDER_NAME", "中标人名称");
        FIELD_LABELS.put("BIDDER_CODE_TYPE", "中标人代码类型");
        FIELD_LABELS.put("WIN_BIDDER_CODE", "中标人代码");
        FIELD_LABELS.put("PRICE_FORM_CODE", "价格形式代码");
    }

    /** 字段中文名（未登记返回 null，调用方回退为英文 code） */
    public static String labelOf(String code) {
        return code == null ? null : FIELD_LABELS.get(code);
    }

    /** 权威必填（VO数据规范 数据集行“必填=是”），新增类型须同步登记 */
    private static final Map<NoticeTypeEnum, Set<String>> REQUIRED_BY_TYPE = Map.of(
            NoticeTypeEnum.TENDER_NOTICE, Set.of(
                    "TENDER_PROJECT_CODE", "NOTICE_BID_NUM", "BID_SECTION_CODES", "UNIFIED_DEAL_CODES",
                    "NOTICE_NAME", "NOTICE_CONTENT", "NOTICE_MEDIA", "URL", "NOTICE_NATURE", "BULLETIN_TYPE",
                    "NOMINAL_FEE", "FILE_RECEIVE_ADDRESS", "DOC_GET_START_TIME", "DOC_GET_END_TIME",
                    "TENDER_DOC_GET_METHOD", "BID_DOC_REFER_END_TIME", "BID_DOC_REFER_METHOD", "SYNDICATED_FLAG",
                    "BULLETIN_DUTY", "FROM_TRADE_PLAT_CODE", "ATTACHMENT_CODE", "SUBMIT_TIMESTAMP",
                    "BULLETIN_DUTY_CODE_TYPE", "PLATFORM_CODE", "PUB_SERVICE_PLAT_CODE", "DATA_TIMESTAMP",
                    "TRADE_PLAT", "PUB_SERVICE_PLAT"),
            NoticeTypeEnum.CHANGE_NOTICE, Set.of(
                    "TENDER_PROJECT_CODE", "NOTICE_BID_NUM", "BID_SECTION_CODES", "UNIFIED_DEAL_CODES",
                    "NOTICE_NAME", "NOTICE_CONTENT", "NOTICE_MEDIA", "URL", "BULLETIN_TYPE", "BID_END_TIME",
                    "CHANGE_CONTENT", "BULLETIN_DUTY", "FROM_TRADE_PLAT_CODE", "ATTACHMENT_CODE", "SUBMIT_TIMESTAMP"),
            NoticeTypeEnum.WIN_CANDIDATE_PUBLICITY, Set.of(
                    "TENDER_PROJECT_CODE", "BID_SECTION_CODES", "UNIFIED_DEAL_CODES",
                    "PUBLICITY_NAME", "PUBLICITY_CONTENT", "NOTICE_MEDIA", "PUBLICITY_TYPE",
                    "PUBLICITY_START_TIME", "PUBLICITY_END_TIME", "IS_CHANGE",
                    "RECOMMEND_RANKING_FIRST", "RECOMMEND_RANKING_FIRST_LEGAL_CODE",
                    "RECOMMEND_RANKING_FIRST_LEGAL_PM_NAME", "RECOMMEND_RANKING_FIRST_WIN_PRICE",
                    "RECOMMEND_RANKING_SECOND_LEGAL_PM_NAME", "RECOMMEND_RANKING_SECOND_WIN_PRICE",
                    "RECOMMEND_RANKING_THIRED_EGAL_PM_NAME", "RECOMMEND_RANKING_THIRED_WIN_PRICE",
                    "BULLETIN_DUTY", "FROM_TRADE_PLAT_CODE", "ATTACHMENT_CODE", "SUBMIT_TIMESTAMP",
                    "PLATFORM_CODE", "PUB_SERVICE_PLAT_CODE", "TRADE_PLAT", "PUB_SERVICE_PLAT",
                    "UNIFIED_DEAL_CODE", "DATA_TIMESTAMP"),
            NoticeTypeEnum.WIN_RESULT_PUBLICITY, Set.of(
                    "TENDER_PROJECT_CODE", "BID_SECTION_CODE", "UNIFIED_DEAL_CODE",
                    "NOTICE_NAME", "NOTICE_CONTENT", "NOTICE_MEDIA", "NOTICE_NATURE", "BULLETIN_TYPE",
                    "WIN_BIDDER_NAME", "BIDDER_CODE_TYPE", "WIN_BIDDER_CODE", "PRICE_FORM_CODE", "IS_CHANGE",
                    "BULLETIN_DUTY", "FROM_TRADE_PLAT_CODE", "ATTACHMENT_CODE", "SUBMIT_TIMESTAMP",
                    "PLATFORM_CODE", "PUB_SERVICE_PLAT_CODE", "TRADE_PLAT", "PUB_SERVICE_PLAT",
                    "DATA_TIMESTAMP"));

    /**
     * 权威必填字段校验（依据《VO数据规范》各数据集行“必填=是”）。
     *
     * @param type      公告类型
     * @param canonical 规整后的字段全集
     * @return 缺失/为空的必填字段名（英文名）；空=校验通过
     */
    public static List<String> missingRequired(NoticeTypeEnum type, LinkedHashMap<String, Object> canonical) {
        List<String> missing = new ArrayList<>();
        Set<String> required = requiredFieldNames(type);
        for (String field : required) {
            Object value = canonical == null ? null : canonical.get(field);
            if (value == null || value.toString().trim().isEmpty()) {
                missing.add(field);
            }
        }
        return missing;
    }

    /** 该类型权威必填字段名集合（供服务层校验与测试构造完整载荷） */
    public static Set<String> requiredFieldNames(NoticeTypeEnum type) {
        return type == null ? Set.of() : REQUIRED_BY_TYPE.getOrDefault(type, Set.of());
    }
}
