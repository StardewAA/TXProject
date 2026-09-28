package cn.iocoder.txgy.module.mes.service.qc.sample;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.module.mes.dal.dataobject.qc.template.MesQcTemplateItemDO;
import cn.iocoder.txgy.module.mes.enums.qc.MesQcSampleTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * MES 检验抽样方案 Service 实现类
 *
 * 抽样规则（R2）：
 * 1. 全检：抽样数 = 基数
 * 2. 固定数量：抽样数 = min(固定数量, 基数)
 * 3. 按比例：抽样数 = 基数 × 比例% 向上取整，且不超过基数
 *
 * 未配置方案（templateItem 为空）或配置非法时，降级为「全检」，保证来源单据流程不被阻断。
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class MesQcSamplePlanServiceImpl implements MesQcSamplePlanService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    @Override
    public MesQcSamplePlanSnapshot calculate(MesQcTemplateItemDO templateItem, BigDecimal baseQuantity) {
        // 1. 基数兜底：为空或非正数时按 0 处理
        BigDecimal base = baseQuantity != null && baseQuantity.compareTo(BigDecimal.ZERO) > 0
                ? baseQuantity : BigDecimal.ZERO;
        // 2. 未配置关联（未绑定抽样方案）时，降级全检
        if (templateItem == null) {
            return buildSnapshot(MesQcSampleTypeEnum.FULL, base, base, null, null, null);
        }

        // 3. 解析抽样方式，非法时降级全检
        MesQcSampleTypeEnum typeEnum = resolveType(templateItem.getSampleType());
        if (typeEnum == null) {
            log.warn("[calculate][抽样方式({}) 非法，降级为全检，templateItemId({})]",
                    templateItem.getSampleType(), templateItem.getId());
            typeEnum = MesQcSampleTypeEnum.FULL;
        }

        // 4. 按抽样方式计算抽样数
        BigDecimal checkQuantity;
        switch (typeEnum) {
            case FIXED_QUANTITY -> {
                BigDecimal fixed = templateItem.getSampleFixedQuantity();
                if (fixed == null || fixed.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("[calculate][固定抽样数量({}) 非法，降级为全检，templateItemId({})]",
                            fixed, templateItem.getId());
                    typeEnum = MesQcSampleTypeEnum.FULL;
                    checkQuantity = base;
                } else {
                    // min(固定数量, 基数)
                    checkQuantity = fixed.min(base);
                }
            }
            case RATIO -> {
                BigDecimal ratio = templateItem.getSampleRatio();
                if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("[calculate][抽样比例({}) 非法，降级为全检，templateItemId({})]",
                            ratio, templateItem.getId());
                    typeEnum = MesQcSampleTypeEnum.FULL;
                    checkQuantity = base;
                } else {
                    // 基数 × 比例% 向上取整，且不超过基数
                    checkQuantity = base.multiply(ratio)
                            .divide(ONE_HUNDRED, 0, RoundingMode.CEILING)
                            .min(base);
                }
            }
            // 全检
            default -> checkQuantity = base;
        }

        return buildSnapshot(typeEnum, base, checkQuantity,
                templateItem.getSampleFixedQuantity(), templateItem.getSampleRatio(), templateItem.getId());
    }

    @Override
    public String toJson(MesQcSamplePlanSnapshot snapshot) {
        return snapshot == null ? null : JSONUtil.toJsonStr(snapshot);
    }

    private MesQcSamplePlanSnapshot buildSnapshot(MesQcSampleTypeEnum typeEnum, BigDecimal base, BigDecimal checkQuantity,
                                                  BigDecimal fixedQuantity, BigDecimal ratio, Long templateItemId) {
        return MesQcSamplePlanSnapshot.builder()
                .sampleType(typeEnum.getType())
                .sampleTypeName(typeEnum.getName())
                .fixedQuantity(fixedQuantity)
                .ratio(ratio)
                .baseQuantity(base)
                .checkQuantity(checkQuantity)
                .templateItemId(templateItemId)
                .snapshotTime(System.currentTimeMillis())
                .build();
    }

    private MesQcSampleTypeEnum resolveType(Integer sampleType) {
        if (ObjUtil.isNull(sampleType)) {
            return null;
        }
        for (MesQcSampleTypeEnum item : MesQcSampleTypeEnum.values()) {
            if (item.getType().equals(sampleType)) {
                return item;
            }
        }
        return null;
    }

}
