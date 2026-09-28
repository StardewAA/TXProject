package cn.iocoder.txgy.module.hrm.service.employee.employment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeNoRuleDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeNoSequenceDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment.HrmEmployeeNoRuleMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment.HrmEmployeeNoSequenceMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_NO_RULE_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_NO_GENERATE_FAILED;

/**
 * HRM 工号生成 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
public class HrmEmployeeNoServiceImpl implements HrmEmployeeNoService {

    /**
     * 不按年重置时使用的周期键
     */
    private static final String PERIOD_ALL = "ALL";

    /**
     * 生成工号时，最多顺延跳过的已占用序号个数（防止序号表与员工档案不一致导致重复）
     */
    private static final int MAX_SKIP_COUNT = 200;

    @Resource
    private HrmEmployeeNoRuleMapper noRuleMapper;
    @Resource
    private HrmEmployeeNoSequenceMapper noSequenceMapper;
    @Resource
    private HrmEmployeeMapper employeeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateJobNumber() {
        // 1. 获得当前租户启用的规则
        HrmEmployeeNoRuleDO rule = getEnabledRule();

        // 2. 加锁取号（并发安全：行锁 + 唯一键）
        String periodKey = getPeriodKey(rule);
        HrmEmployeeNoSequenceDO sequence = noSequenceMapper.selectByRuleIdForUpdate(rule.getId(), periodKey);
        long next;
        if (sequence == null) {
            sequence = new HrmEmployeeNoSequenceDO().setRuleId(rule.getId())
                    .setPeriodKey(periodKey).setCurrentValue(0L);
            noSequenceMapper.insert(sequence);
            next = 1L;
        } else {
            next = (sequence.getCurrentValue() == null ? 0L : sequence.getCurrentValue()) + 1L;
        }
        // 3. 拼接工号；若该序号已被占用（序号表与员工档案不一致时会发生），则顺延到下一个可用序号
        for (int i = 0; i < MAX_SKIP_COUNT; i++) {
            noSequenceMapper.updateById(new HrmEmployeeNoSequenceDO()
                    .setId(sequence.getId()).setCurrentValue(next));
            String jobNumber = format(rule, next);
            if (employeeMapper.selectByJobNumber(jobNumber) == null) {
                return jobNumber;
            }
            next = next + 1;
        }
        throw exception(EMPLOYEE_NO_GENERATE_FAILED, MAX_SKIP_COUNT);
    }

    @Override
    public String previewNextJobNumber() {
        HrmEmployeeNoRuleDO rule = noRuleMapper.selectEnabledRule();
        if (rule == null) {
            return null;
        }
        String periodKey = getPeriodKey(rule);
        HrmEmployeeNoSequenceDO sequence = noSequenceMapper.selectOne(
                new cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX<HrmEmployeeNoSequenceDO>()
                        .eq(HrmEmployeeNoSequenceDO::getRuleId, rule.getId())
                        .eq(HrmEmployeeNoSequenceDO::getPeriodKey, periodKey)
                        .last("LIMIT 1"));
        long next = (sequence == null || sequence.getCurrentValue() == null ? 0L : sequence.getCurrentValue()) + 1L;
        return format(rule, next);
    }

    private HrmEmployeeNoRuleDO getEnabledRule() {
        HrmEmployeeNoRuleDO rule = noRuleMapper.selectEnabledRule();
        if (rule == null || StrUtil.isBlank(rule.getTenantAbbr())) {
            throw exception(EMPLOYEE_NO_RULE_NOT_EXISTS);
        }
        return rule;
    }

    private String getPeriodKey(HrmEmployeeNoRuleDO rule) {
        return rule.getResetPeriod() != null && rule.getResetPeriod() == 1
                ? String.valueOf(LocalDate.now().getYear()) : PERIOD_ALL;
    }

    private String format(HrmEmployeeNoRuleDO rule, long value) {
        int length = rule.getSerialLength() == null || rule.getSerialLength() < 1 ? 6 : rule.getSerialLength();
        return rule.getTenantAbbr() + StrUtil.blankToDefault(rule.getPrefixSeparator(), "")
                + StrUtil.padPre(String.valueOf(value), length, '0');
    }

}
