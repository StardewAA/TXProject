package cn.iocoder.txgy.module.bidding.service.ruleconfig;

import cn.iocoder.txgy.module.bidding.enums.EnterpriseMaterialCategoryEnum;
import cn.iocoder.txgy.module.bidding.enums.EnterpriseMaterialTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.PersonRiskFlagEnum;
import cn.iocoder.txgy.module.bidding.enums.RiskLevelEnum;
import cn.iocoder.txgy.module.bidding.enums.SafetyRiskFlagEnum;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.DictItemRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingDictDataDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig.BiddingDictDataMapper;
import cn.iocoder.txgy.module.bidding.enums.CertFileCategoryEnum;
import cn.iocoder.txgy.module.bidding.enums.CertLifecycleBizDomainEnum;
import cn.iocoder.txgy.module.bidding.enums.CertLifecycleStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.CertLifecycleTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertNotifyChannelEnum;
import cn.iocoder.txgy.module.bidding.enums.CertRenewalTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertRevokeTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertVerifyChannelEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateSourceChannelEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateVerificationStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.EnterpriseMaterialCategoryEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.PersonnelCertStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.PersonnelCertificateTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 业务字典服务实现（DB 字典优先 + 枚举兜底）。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§1.1；用户硬约束"枚举值必须支持动态配置"。
 * <p>设计遵循/针对：① 字典值以 bidding_dict_data 为准（运营可改中文名/标签色、可停用），
 * 但**新增的枚举 code 若尚未配字典**，从枚举类兜底补出（label=code），保证功能可用且不出现"数据凭空消失"；
 * ② 租户级字典覆盖平台默认（同 dict_type + dict_value 时租户行优先），支持个别租户的差异化文案；
 * ③ 类型名（cert_status 等）在契约中固定，未知类型显式报错而不是静默返回空。
 * <p>业务目标：前端枚举展示零硬编码；运营改字典即改前端，无需发版；字典未配置也不影响业务可用性。
 */
@Slf4j
@Service
public class MetaDictServiceImpl implements MetaDictService {

    /** 平台默认行的租户编号（与规则配置同口径）。 */
    private static final long PLATFORM_TENANT_ID = 0L;

    /**
     * 受支持的字典类型 → 枚举类。
     *
     * <p>为什么用枚举类做兜底而不是再维护一份 code 清单：枚举是本模块的代码级事实来源，
     * 兜底直接读枚举可杜绝"枚举加了、字典忘了配、前端下拉少一项"的漏配问题。
     */
    private static final Map<String, Class<? extends Enum<?>>> TYPE_TO_ENUM = new LinkedHashMap<>();

    static {
        TYPE_TO_ENUM.put("cert_status", CertificateStatusEnum.class);
        TYPE_TO_ENUM.put("verification_status", CertificateVerificationStatusEnum.class);
        TYPE_TO_ENUM.put("file_category", CertFileCategoryEnum.class);
        TYPE_TO_ENUM.put("verify_channel", CertVerifyChannelEnum.class);
        TYPE_TO_ENUM.put("source_channel", CertificateSourceChannelEnum.class);
        TYPE_TO_ENUM.put("notify_channel", CertNotifyChannelEnum.class);
        TYPE_TO_ENUM.put("lifecycle_type", CertLifecycleTypeEnum.class);
        TYPE_TO_ENUM.put("lifecycle_status", CertLifecycleStatusEnum.class);
        TYPE_TO_ENUM.put("biz_domain", CertLifecycleBizDomainEnum.class);
        TYPE_TO_ENUM.put("person_cert_status", PersonnelCertStatusEnum.class);
        TYPE_TO_ENUM.put("person_cert_type", PersonnelCertificateTypeEnum.class);
        TYPE_TO_ENUM.put("revoke_type", CertRevokeTypeEnum.class);
        TYPE_TO_ENUM.put("renewal_type", CertRenewalTypeEnum.class);
        // 以下 5 个类型为"注册补齐"（用户核查结论第 3 条）：
        // 前 3 个是安许风险联动新增枚举；后 2 个是企业材料分类/材料类型枚举。
        // 说明：cert_type / holder_type / review_result 在代码里没有对应枚举（取值是字面量集合），
        //       因此只由 bidding_dict_data 种子提供（见 sql/2026-09-19-cert-dict-register.sql）。
        TYPE_TO_ENUM.put("safety_risk_flag", SafetyRiskFlagEnum.class);
        TYPE_TO_ENUM.put("person_risk_flag", PersonRiskFlagEnum.class);
        TYPE_TO_ENUM.put("risk_level", RiskLevelEnum.class);
        TYPE_TO_ENUM.put("material_category", EnterpriseMaterialCategoryEnum.class);
        TYPE_TO_ENUM.put("material_type", EnterpriseMaterialTypeEnum.class);
        TYPE_TO_ENUM.put("material_category", EnterpriseMaterialCategoryEnum.class);
    }

    @Resource
    private BiddingDictDataMapper dictDataMapper;

    @Override
    public Map<String, List<DictItemRespVO>> getDicts(List<String> dictTypes) {
        List<String> types = (dictTypes == null || dictTypes.isEmpty())
                ? new ArrayList<>(TYPE_TO_ENUM.keySet()) : dictTypes;
        // 未知类型显式报错：静默返回空会让前端下拉变空且难以定位
        for (String type : types) {
            if (!TYPE_TO_ENUM.containsKey(type)) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.DICT_TYPE_NOT_SUPPORTED, type);
            }
        }
        Long tenantId = TenantContextHolder.getTenantId();
        // 平台默认行先加载，再用租户行覆盖同名枚举（租户可差异化文案）
        Map<String, DictItemRespVO> merged = new LinkedHashMap<>();
        for (BiddingDictDataDO row : dictDataMapper.selectEnabledByTypes(PLATFORM_TENANT_ID, types)) {
            merged.put(row.getDictType() + "#" + row.getDictValue(), toItem(row));
        }
        if (tenantId != null && tenantId != PLATFORM_TENANT_ID) {
            for (BiddingDictDataDO row : dictDataMapper.selectEnabledByTypes(tenantId, types)) {
                merged.put(row.getDictType() + "#" + row.getDictValue(), toItem(row));
            }
        }
        Map<String, List<DictItemRespVO>> result = new LinkedHashMap<>();
        for (String type : types) {
            List<DictItemRespVO> items = new ArrayList<>();
            for (DictItemRespVO item : merged.values()) {
                if (Objects.equals(item.getDictTypeKey(), type)) {
                    items.add(item);
                }
            }
            // 枚举兜底：字典表里没有的 code 补出来（label 退化为 code，前端仍可正常展示与提交）
            for (Object constant : TYPE_TO_ENUM.get(type).getEnumConstants()) {
                String code = String.valueOf(constant);
                boolean exists = items.stream().anyMatch(i -> Objects.equals(i.getValue(), code));
                if (!exists) {
                    DictItemRespVO fallback = new DictItemRespVO();
                    fallback.setValue(code);
                    fallback.setLabel(code);
                    fallback.setTagType("default");
                    fallback.setTerminal(Boolean.FALSE);
                    fallback.setSort(999);
                    items.add(fallback);
                    log.warn("[meta-dict][fallback] 字典 {} 缺少枚举 {} 的配置，已按 code 兜底下发（建议补配字典）",
                            type, code);
                }
            }
            result.put(type, items);
        }
        return result;
    }

    @Override
    public List<String> supportedTypes() {
        return new ArrayList<>(TYPE_TO_ENUM.keySet());
    }

    private DictItemRespVO toItem(BiddingDictDataDO row) {
        DictItemRespVO vo = new DictItemRespVO();
        vo.setValue(row.getDictValue());
        vo.setLabel(row.getDictLabel());
        vo.setTagType(row.getColorType() == null || row.getColorType().isBlank() ? "default" : row.getColorType());
        vo.setTerminal(Boolean.TRUE.equals(row.getTerminal()));
        vo.setSort(row.getSort());
        // 内部排序键：不参与 JSON 输出（见 VO 上的 @JsonIgnore）
        vo.setDictTypeKey(row.getDictType());
        return vo;
    }
}
