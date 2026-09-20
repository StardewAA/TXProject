package cn.iocoder.txgy.module.hrm.enums.certificate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * HRM 证照到期状态枚举（按有效期实时计算，不落库）
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmCertificateExpireStatusEnum {

    VALID(1, "有效"),
    EXPIRING(2, "临期"),
    EXPIRED(3, "已过期");

    private final Integer status;
    private final String name;

}
