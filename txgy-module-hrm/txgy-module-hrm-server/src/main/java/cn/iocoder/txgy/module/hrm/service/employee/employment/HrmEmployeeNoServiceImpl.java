package cn.iocoder.txgy.module.hrm.service.employee.employment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeNoRuleSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeNoRuleDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeNoSequenceDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment.HrmEmployeeNoRuleMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment.HrmEmployeeNoSequenceMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_NO_RULE_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_NO_GENERATE_FAILED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_NO_RULE_CANNOT_DELETE_ENABLED;

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
            // 首次取号：并发下两个请求会同时插入，唯一键冲突时改为读取已插入的那行（M-06）
            try {
                sequence = new HrmEmployeeNoSequenceDO().setRuleId(rule.getId())
                        .setPeriodKey(periodKey).setCurrentValue(0L);
                noSequenceMapper.insert(sequence);
                next = 1L;
            } catch (DuplicateKeyException ex) {
                sequence = noSequenceMapper.selectByRuleIdForUpdate(rule.getId(), periodKey);
                if (sequence == null) {
                    throw exception(EMPLOYEE_NO_GENERATE_FAILED, MAX_SKIP_COUNT);
                }
                next = (sequence.getCurrentValue() == null ? 0L : sequence.getCurrentValue()) + 1L;
            }
        } else {
            next = (sequence.getCurrentValue() == null ? 0L : sequence.getCurrentValue()) + 1L;
        }
        // 3. 拼接工号；若该序号已被占用（序号表与员工档案不一致时会发生），则顺延到下一个可用序号
        for (int i = 0; i < MAX_SKIP_COUNT; i++) {
            noSequenceMapper.updateById(new HrmEmployeeNoSequenceDO()
                    .setId(sequence.getId()).setCurrentValue(next));
            String jobNumber = format(rule, next);
            // 占用判断包含「已删除档案」：与唯一索引口径一致，工号永不复用
            if (employeeMapper.countByJobNumberIncludeDeleted(jobNumber) == 0) {
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
        // 与正式取号口径一致：跳过已被占用的序号（含已删除档案占用），避免「预览」与「实际」不一致（L-03）
        for (int i = 0; i < MAX_SKIP_COUNT; i++) {
            String jobNumber = format(rule, next);
            if (employeeMapper.countByJobNumberIncludeDeleted(jobNumber) == 0) {
                return jobNumber;
            }
            next = next + 1;
        }
        return format(rule, next);
    }

    @Override
    public List<HrmEmployeeNoRuleDO> getRuleList() {
        return noRuleMapper.selectList();
    }

    @Override
    public HrmEmployeeNoRuleDO getRule(Long id) {
        return noRuleMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveRule(HrmEmployeeNoRuleSaveReqVO reqVO) {
        HrmEmployeeNoRuleDO rule = BeanUtils.toBean(reqVO, HrmEmployeeNoRuleDO.class);
        rule.setSerialLength(reqVO.getSerialLength())
                .setResetPeriod(reqVO.getResetPeriod() == null ? 0 : reqVO.getResetPeriod())
                .setAllowManual(Boolean.TRUE.equals(reqVO.getAllowManual()))
                .setStatus(reqVO.getStatus() == null ? 0 : reqVO.getStatus());
        if (rule.getId() != null) {
            // 修改时校验存在
            if (noRuleMapper.selectById(rule.getId()) == null) {
                throw exception(EMPLOYEE_NO_RULE_NOT_EXISTS);
            }
        }
        noRuleMapper.insertOrUpdate(rule);
        // 同一租户只保留一条启用规则：启用本条时，其余规则自动停用
        if (Integer.valueOf(0).equals(rule.getStatus())) {
            for (HrmEmployeeNoRuleDO other : noRuleMapper.selectList()) {
                if (!other.getId().equals(rule.getId()) && Integer.valueOf(0).equals(other.getStatus())) {
                    noRuleMapper.updateById(new HrmEmployeeNoRuleDO().setId(other.getId()).setStatus(1));
                }
            }
        }
        return rule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        HrmEmployeeNoRuleDO rule = noRuleMapper.selectById(id);
        if (rule == null) {
            throw exception(EMPLOYEE_NO_RULE_NOT_EXISTS);
        }
        // 启用中的规则不允许直接删除，避免删完新员工建不出来
        if (Integer.valueOf(0).equals(rule.getStatus())) {
            throw exception(EMPLOYEE_NO_RULE_CANNOT_DELETE_ENABLED);
        }
        noRuleMapper.deleteById(id);
    }

    @Override
    public String previewSampleJobNumber(HrmEmployeeNoRuleSaveReqVO reqVO) {
        HrmEmployeeNoRuleDO rule = new HrmEmployeeNoRuleDO()
                .setTenantAbbr(reqVO.getTenantAbbr())
                .setSerialLength(reqVO.getSerialLength())
                .setPrefixSeparator(reqVO.getPrefixSeparator())
                .setResetPeriod(reqVO.getResetPeriod());
        return format(rule, 1L);
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
        // 启用「按年重置」时，把年份拼进工号（例如 QC202600001），
        // 否则新一年的序号会与历史工号冲突、只能一路跳号到最大值（H-08）
        String yearPart = rule.getResetPeriod() != null && rule.getResetPeriod() == 1
                ? String.valueOf(LocalDate.now().getYear()) : "";
        return rule.getTenantAbbr() + StrUtil.blankToDefault(rule.getPrefixSeparator(), "") + yearPart
                + StrUtil.padPre(String.valueOf(value), length, '0');
    }

}
