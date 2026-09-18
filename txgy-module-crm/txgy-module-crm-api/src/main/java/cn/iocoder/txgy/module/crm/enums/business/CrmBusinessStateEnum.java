package cn.iocoder.txgy.module.crm.enums.business;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 商机状态组枚举（只读派生）
 *
 * <p>由「商机阶段 + 赢单/输单动作」自动映射，前端只读、不提供编辑入口：
 * <ul>
 *     <li>商机待评估 → 未开始</li>
 *     <li>初步接洽 / 需求确认 / 方案评估 / 商务谈判 → 进行中</li>
 *     <li>点击「赢单」 → 已赢单</li>
 *     <li>点击「输单」 → 已丢单</li>
 * </ul>
 *
 * <p>对应字典 {@link cn.iocoder.txgy.module.crm.enums.DictTypeConstants#CRM_BUSINESS_STATE}
 *
 * @author 防腐保温项目组
 */
@RequiredArgsConstructor
@Getter
public enum CrmBusinessStateEnum implements ArrayValuable<Integer> {

    NOT_STARTED(1, "未开始"),
    IN_PROGRESS(2, "进行中"),
    WON(3, "已赢单"),
    LOST(4, "已丢单"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(CrmBusinessStateEnum::getState).toArray(Integer[]::new);

    /**
     * 状态组值
     */
    private final Integer state;
    /**
     * 状态组名称
     */
    private final String name;

    public static CrmBusinessStateEnum getByState(Integer state) {
        if (state == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> ObjUtil.equal(item.getState(), state))
                .findFirst().orElse(null);
    }

    public static String getNameByState(Integer state) {
        CrmBusinessStateEnum stateEnum = getByState(state);
        return stateEnum == null ? null : stateEnum.getName();
    }

    /**
     * 是否为已关闭（已赢单 / 已丢单）；关闭后商机关键字段只读
     */
    public static boolean isClosed(Integer state) {
        return ObjUtil.equal(WON.getState(), state) || ObjUtil.equal(LOST.getState(), state);
    }

    /**
     * 根据阶段推导状态组（赢单/丢单由动作直接指定，不走这里）
     *
     * @param stage 商机阶段
     * @return 状态组
     */
    public static Integer getStateByStage(Integer stage) {
        return CrmBusinessStageEnum.isPending(stage) ? NOT_STARTED.getState() : IN_PROGRESS.getState();
    }

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
