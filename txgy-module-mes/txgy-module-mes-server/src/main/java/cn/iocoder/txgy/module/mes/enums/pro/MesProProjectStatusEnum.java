package cn.iocoder.txgy.module.mes.enums.pro;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * MES 项目状态枚举
 *
 * 字典 {@link cn.iocoder.txgy.module.mes.enums.DictTypeConstants#MES_PRO_PROJECT_STATUS}
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum MesProProjectStatusEnum {

    NOT_STARTED(0, "未开始"),
    PROCESSING(1, "进行中"),
    FINISHED(2, "已完成"),
    PAUSED(3, "已暂停"),
    CANCELED(4, "已取消"),
    ;

    private final Integer status;
    private final String name;

    public static MesProProjectStatusEnum valueOfStatus(Integer status) {
        if (status == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.getStatus().equals(status))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断项目是否已关闭（已完成 / 已取消）
     *
     * 已关闭的项目不允许再挂接生产工单
     *
     * @param status 项目状态
     * @return 是否已关闭
     */
    public static boolean isClosed(Integer status) {
        MesProProjectStatusEnum statusEnum = valueOfStatus(status);
        return statusEnum == FINISHED || statusEnum == CANCELED;
    }

    /**
     * 判断是否为人工状态（已暂停 / 已取消）
     *
     * 人工状态不会被工单进度自动覆盖，只能由用户手动变更
     *
     * @param status 项目状态
     * @return 是否为人工状态
     */
    public static boolean isManual(Integer status) {
        MesProProjectStatusEnum statusEnum = valueOfStatus(status);
        return statusEnum == PAUSED || statusEnum == CANCELED;
    }

    /**
     * 判断当前状态是否允许流转到目标状态（编辑保存时的状态变更校验）
     *
     * 规则：
     * 1. 已完成 / 已取消为终态，不允许再变更为其他状态
     * 2. 未开始 -> 进行中 / 已暂停 / 已取消
     * 3. 进行中 -> 已完成 / 已暂停 / 已取消
     * 4. 已暂停 -> 进行中（恢复）/ 已取消（结项请走强制结项）
     *
     * @param current 当前状态
     * @param target  目标状态
     * @return 是否允许流转
     */
    public static boolean canTransferTo(Integer current, Integer target) {
        if (current == null || target == null || current.equals(target)) {
            return true;
        }
        MesProProjectStatusEnum currentEnum = valueOfStatus(current);
        if (currentEnum == null || valueOfStatus(target) == null) {
            return false;
        }
        switch (currentEnum) {
            case NOT_STARTED:
                return target.equals(PROCESSING.status) || target.equals(PAUSED.status)
                        || target.equals(CANCELED.status);
            case PROCESSING:
                return target.equals(FINISHED.status) || target.equals(PAUSED.status)
                        || target.equals(CANCELED.status);
            case PAUSED:
                return target.equals(PROCESSING.status) || target.equals(CANCELED.status);
            default:
                // 已完成 / 已取消为终态，不允许变更
                return false;
        }
    }

}
