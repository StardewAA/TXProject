package cn.iocoder.txgy.module.hrm.enums.recruit.demand;

import cn.hutool.core.collection.CollUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * HRM 用工需求单状态枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmRecruitDemandStatusEnum {

    PENDING(1, "待招聘"),
    RECRUITING(2, "招聘中"),
    FINISHED(3, "已完成"),
    CANCELED(4, "已取消");

    /**
     * 状态
     */
    private final Integer status;
    /**
     * 名称
     */
    private final String name;

    public static HrmRecruitDemandStatusEnum valueOf(Integer status) {
        for (HrmRecruitDemandStatusEnum item : values()) {
            if (item.getStatus().equals(status)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 允许的下一状态（状态流转矩阵，Q-009 / I-009）
     *
     * <p>口径：</p>
     * <ul>
     *   <li>待招聘 → 招聘中 / 已取消</li>
     *   <li>招聘中 → 已完成 / 已取消 / 待招聘（暂停招聘时回到待招聘）</li>
     *   <li>已完成 → 招聘中（需继续招同一需求单时重新开启）/ 已取消</li>
     *   <li>已取消 → 待招聘（重新开启必须从待招聘开始，不允许直接跳到招聘中）</li>
     * </ul>
     *
     * @return 允许迁移到的状态列表
     */
    public List<HrmRecruitDemandStatusEnum> getNextStatuses() {
        return switch (this) {
            case PENDING -> List.of(RECRUITING, CANCELED);
            case RECRUITING -> List.of(FINISHED, CANCELED, PENDING);
            case FINISHED -> List.of(RECRUITING, CANCELED);
            case CANCELED -> List.of(PENDING);
        };
    }

    /**
     * 是否允许从当前状态迁移到目标状态
     *
     * @param target 目标状态
     * @return 允许返回 true
     */
    public boolean canTransferTo(HrmRecruitDemandStatusEnum target) {
        if (target == null || target == this) {
            return true;
        }
        return CollUtil.contains(getNextStatuses(), target);
    }

}
