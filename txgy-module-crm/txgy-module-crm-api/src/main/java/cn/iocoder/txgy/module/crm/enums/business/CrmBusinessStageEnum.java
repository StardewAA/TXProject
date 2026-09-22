package cn.iocoder.txgy.module.crm.enums.business;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 商机阶段枚举
 *
 * <p>标准方案：商机阶段只表达「推进过程」，赢单/输单不再作为阶段，
 * 改由「赢单」「输单」按钮驱动（见 {@link CrmBusinessStateEnum}）。
 *
 * <p>对应字典 {@link cn.iocoder.txgy.module.crm.enums.DictTypeConstants#CRM_BUSINESS_STAGE}
 *
 * @author 防腐保温项目组
 */
@RequiredArgsConstructor
@Getter
public enum CrmBusinessStageEnum implements ArrayValuable<Integer> {

    PENDING(1, "商机待评估", 5),
    INITIAL(2, "初步接洽", 10),
    DEMAND(3, "需求确认", 40),
    SCHEME(4, "方案评估", 60),
    NEGOTIATION(5, "商务谈判", 70),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(CrmBusinessStageEnum::getStage).toArray(Integer[]::new);

    /**
     * 阶段值
     */
    private final Integer stage;
    /**
     * 阶段名称
     */
    private final String name;
    /**
     * 预估赢单率（%）
     */
    private final Integer percent;

    public static CrmBusinessStageEnum getByStage(Integer stage) {
        if (stage == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> ObjUtil.equal(item.getStage(), stage))
                .findFirst().orElse(null);
    }

    public static String getNameByStage(Integer stage) {
        CrmBusinessStageEnum stageEnum = getByStage(stage);
        return stageEnum == null ? null : stageEnum.getName();
    }

    /**
     * 阶段对应的预估赢单率（%）
     *
     * @param stage 阶段
     * @return 赢单率；阶段非法时返回默认阶段（商机待评估）的赢单率
     */
    public static Integer getPercentByStage(Integer stage) {
        CrmBusinessStageEnum stageEnum = getByStage(stage);
        return stageEnum == null ? PENDING.getPercent() : stageEnum.getPercent();
    }

    /**
     * 默认阶段（线索转商机时的默认值）
     */
    public static Integer getDefaultStage() {
        return PENDING.getStage();
    }

    /**
     * 是否为「商机待评估」阶段（该阶段对应状态组=未开始）
     */
    public static boolean isPending(Integer stage) {
        return ObjUtil.equal(PENDING.getStage(), stage);
    }

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
