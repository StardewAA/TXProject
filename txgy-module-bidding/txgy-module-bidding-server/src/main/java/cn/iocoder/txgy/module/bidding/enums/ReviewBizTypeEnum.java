package cn.iocoder.txgy.module.bidding.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审查任务业务类型（字典 review_biz_type）。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§2/§3。
 * <p>为什么放在枚举里：建档接口按业务域触发任务时需要一个**确定的**类型码（拼错就会产生无法识别的任务）。
 *
 * @author txgy
 */
@Getter
@AllArgsConstructor
public enum ReviewBizTypeEnum {

    QUALIFICATION("企业资质"),
    CERTIFICATE("证照"),
    PERSON_CERT("员工证书");

    private final String label;

}
