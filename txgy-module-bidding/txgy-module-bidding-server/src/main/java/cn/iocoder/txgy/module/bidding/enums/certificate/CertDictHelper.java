package cn.iocoder.txgy.module.bidding.enums.certificate;

import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingDictDataDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig.BiddingDictDataMapper;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电子证照域字典解析组件（**容错**）：字典不可用/未命中时返回 null，绝不因"取中文名失败"让业务接口 500。
 *
 * <p>为什么容错：中文名是**展示增强**，不是业务事实（code 才是权威，规范 §5 三-7）。
 * 前端拿到 null 时应回退显示 code。
 *
 * <p><b>为什么读 bidding_dict_data 而不是 {@code DictFrameworkUtils}</b>：
 * 2026-09-21 实测发现——电子证照域字典注册在**本模块的 {@code bidding_dict_data}**（32 行），
 * 而框架的 {@code DictFrameworkUtils} 只读**框架的 {@code system_dict_data}**（cert* 0 行），
 * 两者不是同一张表、也没有同步桥。此前用 DictFrameworkUtils 解析的结果是：
 * <b>所有 *Name 字段恒为 null</b>——而"容错返回 null"把这种**永久性错配**伪装成了"偶发字典不可用"，
 * 所以一直没被发现。改为直接读本模块字典表后中文名才真正出得来。
 *
 * <p><b>租户口径</b>：平台租户 0 打底 + 当前租户覆盖同名 value（与 MetaDictServiceImpl / 证书服务同口径）。
 */
@Slf4j
@Component
public class CertDictHelper {

    /** 平台默认行的租户编号（电子证照域字典统一注册在租户 0）。 */
    private static final long PLATFORM_TENANT_ID = 0L;

    @Resource
    private BiddingDictDataMapper biddingDictDataMapper;

    /**
     * 按字典解析中文名（容错）。
     *
     * @param dictType 字典类型（见 {@link CertDictTypes}）
     * @param code     取值 code；为空返回 null
     * @return 中文名；未命中/字典不可用时返回 null（前端回退显示 code）
     */
    public String label(String dictType, String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        Map<String, String> labels = labelMap(dictType);
        return labels.get(code);
    }

    /**
     * 取整本字典的 value→label 映射（一次查询多处复用，避免逐字段查库）。
     *
     * <p>用 {@code dict_label} 作为展示名。注意 {@code cert_type_category} 是**映射字典**
     * （value=证照类型 code，label=大类 code），它的"展示名"语义就是大类 code，属预期。
     */
    public Map<String, String> labelMap(String dictType) {
        Map<String, String> merged = new LinkedHashMap<>();
        try {
            for (BiddingDictDataDO row : biddingDictDataMapper.selectEnabledByType(PLATFORM_TENANT_ID, dictType)) {
                merged.put(row.getDictValue(), row.getDictLabel());
            }
            Long tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null && tenantId != PLATFORM_TENANT_ID) {
                for (BiddingDictDataDO row : biddingDictDataMapper.selectEnabledByType(tenantId, dictType)) {
                    merged.put(row.getDictValue(), row.getDictLabel());
                }
            }
        } catch (Exception e) {
            // 容错：字典查询失败只影响中文名，不影响业务数据（code 仍照常下发）
            log.warn("[CertDictHelper] 字典查询失败（回退空映射，前端显示 code）dictType={} err={}",
                    dictType, e.getMessage());
            return new HashMap<>();
        }
        return merged;
    }
}
