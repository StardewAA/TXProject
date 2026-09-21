package cn.iocoder.txgy.module.hrm.service.employee.employment;

/**
 * HRM 工号生成 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeNoService {

    /**
     * 为当前租户生成一个工号（格式：租户缩写 + 顺序号）
     *
     * @return 工号
     */
    String generateJobNumber();

    /**
     * 预览当前租户下一个工号（不占用序号）
     *
     * @return 下一个工号；无规则时返回 null
     */
    String previewNextJobNumber();

}
