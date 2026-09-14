package cn.iocoder.txgy.module.bidding.service.subject;
// [依据] 主体“信息完善”必填清单（投标/发布资格门的数据侧）：
//   单位名称/统一社会信用代码/法定代表人/法人机构类别/行政区划/联系人/电话 ← 国家+陕西 V2.0 数据规范 表89 法人基本信息必填项
//   + 《电子招标投标办法》第24条（投标人注册登记、如实递交并经平台验证——本清单即“如实递交并经验证”的最小可验证集）；
//   招标人侧同构（招标人身份字段同源于表89 + 陕 V2.0 ProjectSubjectExtVO 招标人主体）。
// 注：授权由 SubjectAuthDO 承载，与完善度分离：仅授权未完善 → 不可投标/发布（需求口径）。

import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectProfileDO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主体档案完善度规则（唯一事实源）：必填 code → 中文名 → DO 取值判定。
 */
public final class SubjectCompleteness {

    /** 必填字段（顺序即缺失项展示顺序）；key 作为 missingRequiredCodes 返回 */
    public static final Map<String, String> REQUIRED_FIELDS = new LinkedHashMap<>();

    static {
        REQUIRED_FIELDS.put("subjectName", "单位名称");
        REQUIRED_FIELDS.put("uscc", "统一社会信用代码");
        REQUIRED_FIELDS.put("legalPersonName", "法定代表人姓名");
        REQUIRED_FIELDS.put("legalPersonTypeCode", "法人机构类别");
        REQUIRED_FIELDS.put("regionCode", "行政区划");
        REQUIRED_FIELDS.put("contactName", "联系人");
        REQUIRED_FIELDS.put("contactPhone", "联系电话");
    }

    /** 核算结果 */
    public static final class Assessment {
        public final boolean complete;
        public final List<String> missingCodes;
        public final List<String> missingNames;

        Assessment(boolean complete, List<String> missingCodes, List<String> missingNames) {
            this.complete = complete;
            this.missingCodes = missingCodes;
            this.missingNames = missingNames;
        }
    }

    private SubjectCompleteness() {
    }

    /** 核算档案完善度（档案为 null 视为全缺） */
    public static Assessment assess(SubjectProfileDO profile) {
        if (profile == null) {
            return new Assessment(false, new ArrayList<>(REQUIRED_FIELDS.keySet()),
                    new ArrayList<>(REQUIRED_FIELDS.values()));
        }
        List<String> missingCodes = new ArrayList<>();
        List<String> missingNames = new ArrayList<>();
        REQUIRED_FIELDS.forEach((code, name) -> {
            if (!hasValue(profile, code)) {
                missingCodes.add(code);
                missingNames.add(name);
            }
        });
        return new Assessment(missingCodes.isEmpty(), missingCodes, missingNames);
    }

    private static boolean hasValue(SubjectProfileDO p, String code) {
        switch (code) {
            case "subjectName": return StringUtils.hasText(p.getSubjectName());
            case "uscc": return StringUtils.hasText(p.getUscc());
            case "legalPersonName": return StringUtils.hasText(p.getLegalPersonName());
            case "legalPersonTypeCode": return StringUtils.hasText(p.getLegalPersonTypeCode());
            case "regionCode": return StringUtils.hasText(p.getRegionCode());
            case "contactName": return StringUtils.hasText(p.getContactName());
            case "contactPhone": return StringUtils.hasText(p.getContactPhone());
            default: return true;
        }
    }
}
