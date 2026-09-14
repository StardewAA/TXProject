package cn.iocoder.txgy.module.bidding.service.project;

// [规范依据]《招投标模块开发规范》§2.1/2.2（全量 VO 与 @PublicLevel 公开级别裁剪）、§3.2（页面另建 VO）；
//           数据规范 V2.0 数据集 ProjectVO / TenderProjectVO / BidSectionVO 字段全集与"必填=是"口径。
// [业务目标] 项目域载荷规整与必填校验统一入口：按数据集 record 字段顺序补 null（"该有的字段一个不能少"），
//           出参按 @PublicLevel(SOCIAL) 裁剪（公开只读面不泄漏政务字段）。
// [为什么] 与 notice 域同构但类型不同（项目域三种数据集），独立工具避免与公告域互相影响（公告域 NoticePayloadUtils 保持不变）。

import cn.iocoder.txgy.module.bidding.api.enums.PublicLevel;
import cn.iocoder.txgy.module.bidding.api.enums.PublicLevelEnum;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProjectPayloadUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 字段中文字典（报错与前端模板用；覆盖项目域三数据集必填键，新增字段须同步登记） */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("PROJECT_CODE", "项目编号");
        LABELS.put("PROJECT_NAME", "项目名称");
        LABELS.put("REGION_CODE", "行政区划代码");
        LABELS.put("INVEST_PROJECT_CODE", "投资项目统一代码");
        LABELS.put("INDUSTRIES_TYPE", "行业类别");
        LABELS.put("FUND_SOURCE", "资金来源");
        LABELS.put("PROJECT_SCALE", "项目规模");
        LABELS.put("TENDER_PROJECT_CODE", "招标项目编号");
        LABELS.put("TENDER_PROJECT_NAME", "招标项目名称");
        LABELS.put("TENDER_PROJECT_TYPE", "招标项目类型");
        LABELS.put("TENDERER_NAME", "招标人名称");
        LABELS.put("TENDER_AGENCY_NAME", "招标代理机构名称");
        LABELS.put("TENDER_MODE", "招标方式");
        LABELS.put("QULIFICATION_CHECK_MODE", "资格审查方式");
        LABELS.put("TENDER_ORGANIZE_FORM", "招标组织形式");
        LABELS.put("BID_SECTION_CODE", "标段（包）编号");
        LABELS.put("BID_SECTION_NAME", "标段（包）名称");
        LABELS.put("CONTRACT_RECKON_PRICE", "合同估算价");
        LABELS.put("BID_QUALIFICATION", "投标人资格条件");
        LABELS.put("TIME_LIMIT", "工期/服务期");
    }

    private ProjectPayloadUtils() {
    }

    /** 字段中文名（未登记回退英文 code） */
    public static String labelOf(String code) {
        return code == null ? null : LABELS.getOrDefault(code, code);
    }

    /**
     * 按数据集 record 规整载荷：字段名照录规范、顺序即 record 声明序、缺失补 null（多余键丢弃）。
     */
    public static LinkedHashMap<String, Object> canonicalize(Class<?> datasetClass, Map<String, Object> raw) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (datasetClass == null) {
            return result;
        }
        RecordComponent[] components = datasetClass.getRecordComponents();
        for (RecordComponent component : components) {
            Object value = raw == null ? null : raw.get(component.getName());
            result.put(component.getName(), value);
        }
        return result;
    }

    /** 公开只读裁剪：仅保留 @PublicLevel(SOCIAL) 且非空的字段（规范 §2.2 公开级别裁剪） */
    public static LinkedHashMap<String, Object> socialSubset(Class<?> datasetClass, LinkedHashMap<String, Object> canonical) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (datasetClass == null || canonical == null) {
            return result;
        }
        for (RecordComponent component : datasetClass.getRecordComponents()) {
            PublicLevel level = component.getAnnotation(PublicLevel.class);
            if (level != null && level.value() == PublicLevelEnum.SOCIAL && canonical.get(component.getName()) != null) {
                result.put(component.getName(), canonical.get(component.getName()));
            }
        }
        return result;
    }

    /** 必填校验（数据集"必填=是"）：返回缺失字段的中文名列表；空=通过 */
    public static List<String> missingRequired(Map<String, Object> canonical, String... requiredCodes) {
        List<String> missing = new ArrayList<>();
        for (String code : requiredCodes) {
            Object value = canonical == null ? null : canonical.get(code);
            if (value == null || value.toString().trim().isEmpty()) {
                missing.add(labelOf(code));
            }
        }
        return missing;
    }

    /** 序列化（null/异常→null） */
    public static String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
