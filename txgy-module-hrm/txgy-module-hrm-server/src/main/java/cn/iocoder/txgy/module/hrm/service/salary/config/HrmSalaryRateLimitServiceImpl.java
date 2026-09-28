package cn.iocoder.txgy.module.hrm.service.salary.config;

import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.ratelimit.HrmSalaryRateLimitSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config.HrmSalaryRateLimitDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.salary.config.HrmSalaryRateLimitMapper;
import cn.iocoder.txgy.module.hrm.enums.salary.config.HrmSalaryRateLimitBizTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.INSURANCE_RATE_LIMIT_EXCEEDED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.SALARY_RATE_LIMIT_DATA_ILLEGAL;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.SALARY_RATE_LIMIT_EXCEEDED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.SALARY_RATE_LIMIT_NOT_EXISTS;

/**
 * HRM 比例上限配置 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmSalaryRateLimitServiceImpl implements HrmSalaryRateLimitService {

    @Resource
    private HrmSalaryRateLimitMapper rateLimitMapper;

    @Override
    public List<HrmSalaryRateLimitDO> getRateLimitList(Integer bizType) {
        return rateLimitMapper.selectListByBizType(bizType);
    }

    @Override
    public void updateRateLimit(HrmSalaryRateLimitSaveReqVO reqVO) {
        // 1. 校验配置存在
        HrmSalaryRateLimitDO config = rateLimitMapper.selectById(reqVO.getId());
        if (config == null) {
            throw exception(SALARY_RATE_LIMIT_NOT_EXISTS);
        }
        // 2. 按业务类型校验必填（考勤项填一个上限，参保项填公司与个人两个上限）
        if (HrmSalaryRateLimitBizTypeEnum.ATTENDANCE_RULE.getType().equals(config.getBizType())) {
            if (reqVO.getRateLimit() == null) {
                throw exception(SALARY_RATE_LIMIT_DATA_ILLEGAL);
            }
        } else {
            if (reqVO.getCorporateRateLimit() == null || reqVO.getPersonalRateLimit() == null) {
                throw exception(SALARY_RATE_LIMIT_DATA_ILLEGAL);
            }
        }
        // 3. 更新
        rateLimitMapper.updateById(new HrmSalaryRateLimitDO().setId(config.getId())
                .setRateLimit(reqVO.getRateLimit())
                .setCorporateRateLimit(reqVO.getCorporateRateLimit())
                .setPersonalRateLimit(reqVO.getPersonalRateLimit())
                .setStatus(reqVO.getStatus())
                .setRemark(reqVO.getRemark()));
    }

    @Override
    public HrmSalaryRateLimitDO getRateLimit(HrmSalaryRateLimitBizTypeEnum bizType, String itemCode) {
        if (bizType == null || itemCode == null) {
            return null;
        }
        return rateLimitMapper.selectByBizTypeAndItemCode(bizType.getType(), itemCode);
    }

    @Override
    public void validateAttendanceRate(String itemCode, String itemName, BigDecimal rate) {
        if (rate == null) {
            return;
        }
        HrmSalaryRateLimitDO config = getRateLimit(HrmSalaryRateLimitBizTypeEnum.ATTENDANCE_RULE, itemCode);
        if (isDisabled(config)) {
            return;
        }
        BigDecimal limit = config == null || config.getRateLimit() == null
                ? DEFAULT_RATE_LIMIT : config.getRateLimit();
        if (rate.compareTo(limit) > 0) {
            throw exception(SALARY_RATE_LIMIT_EXCEEDED, itemName, format(rate), format(limit), RATE_LIMIT_CONFIG_HINT);
        }
    }

    @Override
    public void validateInsuranceRate(Integer projectType, String projectName,
                                      BigDecimal corporateRate, BigDecimal personalRate) {
        if (projectType == null) {
            return;
        }
        HrmSalaryRateLimitDO config =
                getRateLimit(HrmSalaryRateLimitBizTypeEnum.INSURANCE_SCHEME, String.valueOf(projectType));
        if (isDisabled(config)) {
            return;
        }
        BigDecimal corporateLimit = config == null || config.getCorporateRateLimit() == null
                ? DEFAULT_RATE_LIMIT : config.getCorporateRateLimit();
        BigDecimal personalLimit = config == null || config.getPersonalRateLimit() == null
                ? DEFAULT_RATE_LIMIT : config.getPersonalRateLimit();
        if (corporateRate != null && corporateRate.compareTo(corporateLimit) > 0) {
            throw exception(INSURANCE_RATE_LIMIT_EXCEEDED, projectName, "公司",
                    format(corporateRate), format(corporateLimit), RATE_LIMIT_CONFIG_HINT);
        }
        if (personalRate != null && personalRate.compareTo(personalLimit) > 0) {
            throw exception(INSURANCE_RATE_LIMIT_EXCEEDED, projectName, "个人",
                    format(personalRate), format(personalLimit), RATE_LIMIT_CONFIG_HINT);
        }
    }

    private boolean isDisabled(HrmSalaryRateLimitDO config) {
        return config != null && Integer.valueOf(1).equals(config.getStatus());
    }

    /**
     * 比例展示：去掉无意义的小数尾零（16.00 → 16，0.70 → 0.7）
     */
    private String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

}
