package cn.iocoder.txgy.module.hrm.service.salary.config;

import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.ratelimit.HrmSalaryRateLimitSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config.HrmSalaryRateLimitDO;
import cn.iocoder.txgy.module.hrm.enums.salary.config.HrmSalaryRateLimitBizTypeEnum;

import java.math.BigDecimal;
import java.util.List;

/**
 * HRM 比例上限配置 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmSalaryRateLimitService {

    /**
     * 未配置时的兜底上限（%）
     */
    BigDecimal DEFAULT_RATE_LIMIT = new BigDecimal("100.00");
    /**
     * 超限提示里给出的配置入口
     */
    String RATE_LIMIT_CONFIG_HINT = "薪资管理 → 比例上限";

    /**
     * 获得比例上限配置列表
     *
     * @param bizType 业务类型，为空时返回全部
     */
    List<HrmSalaryRateLimitDO> getRateLimitList(Integer bizType);

    /**
     * 修改比例上限配置
     */
    void updateRateLimit(HrmSalaryRateLimitSaveReqVO reqVO);

    /**
     * 获得某业务类型下某个项的比例上限配置
     *
     * @param bizType  业务类型
     * @param itemCode 项目编码
     * @return 配置，不存在时返回 null
     */
    HrmSalaryRateLimitDO getRateLimit(HrmSalaryRateLimitBizTypeEnum bizType, String itemCode);

    /**
     * 校验考勤计薪项比例是否超过配置上限
     *
     * @param itemCode 计薪项编码
     * @param itemName 计薪项名称（用于报错文案）
     * @param rate     比例（%）
     */
    void validateAttendanceRate(String itemCode, String itemName, BigDecimal rate);

    /**
     * 校验参保项目缴纳比例是否超过配置上限
     *
     * @param projectType   参保项目类型
     * @param projectName   参保项目名称（用于报错文案）
     * @param corporateRate 公司缴纳比例（%）
     * @param personalRate  个人缴纳比例（%）
     */
    void validateInsuranceRate(Integer projectType, String projectName,
                               BigDecimal corporateRate, BigDecimal personalRate);

}
