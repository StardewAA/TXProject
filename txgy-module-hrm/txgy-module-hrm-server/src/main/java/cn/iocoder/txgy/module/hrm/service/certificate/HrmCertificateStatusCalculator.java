package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;

import java.time.LocalDate;

/**
 * HRM 证件状态计算器
 *
 * 证件状态与剩余天数是派生数据，台账、续证、预警三处共用同一套口径，
 * 因此集中在本组件实现，避免多处规则漂移。
 *
 * @author OPENLAB BS
 */
public interface HrmCertificateStatusCalculator {

    /**
     * 证件类型未配置提前预警天数时的兜底值
     */
    int DEFAULT_ADVANCE_DAYS = 30;

    /**
     * 计算证件状态
     *
     * 规则：注销状态保持不变；无有效结束日期视为有效；已过结束日期为过期；
     * 剩余天数在提前预警期内为即将到期；其余为有效。
     *
     * @param certificate     证件台账记录
     * @param certificateType 证件类型，可为空
     * @param today           计算基准日期
     * @return 证件状态，参见 {@link cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateStatusEnum}
     */
    Integer calculateStatus(HrmEmployeeCertificateDO certificate, HrmCertificateTypeDO certificateType,
                            LocalDate today);

    /**
     * 计算距到期剩余天数，无有效结束日期时返回 null
     *
     * @param certificate 证件台账记录
     * @param today       计算基准日期
     * @return 剩余天数，负数表示已过期
     */
    Integer calculateRemainDays(HrmEmployeeCertificateDO certificate, LocalDate today);

    /**
     * 获得证件类型的提前预警天数，未配置时返回 {@link #DEFAULT_ADVANCE_DAYS}
     *
     * @param certificateType 证件类型，可为空
     * @return 提前预警天数
     */
    int getAdvanceDays(HrmCertificateTypeDO certificateType);

}
