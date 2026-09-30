package cn.iocoder.txgy.module.bidding.service.safety;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskRulePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskRuleRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskRuleSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.safety.SafetyRiskRuleDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.safety.SafetyRiskRuleMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 安许风险规则服务实现（契约 e2e P1 方案 A）。
 *
 * <p>设计遵循/针对：① 分页用 LambdaQueryWrapperX 的 likeIfPresent/eqIfPresent，不手写 SQL；
 * ② update 的 version 由 {@code @Version} CAS 兜底——不匹配时 updateById 返回 0，转业务码而非静默成功；
 * ③ delete 走 {@code @TableLogic} 逻辑删除（与模块其他台账一致）。
 */
@Slf4j
@Service
public class SafetyRiskRuleServiceImpl implements SafetyRiskRuleService {

    @Resource
    private SafetyRiskRuleMapper safetyRiskRuleMapper;

    @Override
    public PageResult<SafetyRiskRuleRespVO> page(SafetyRiskRulePageReqVO reqVO) {
        PageResult<SafetyRiskRuleDO> page = safetyRiskRuleMapper.selectPage(reqVO,
                new LambdaQueryWrapperX<SafetyRiskRuleDO>()
                        .likeIfPresent(SafetyRiskRuleDO::getRuleName, reqVO.getRuleName())
                        .eqIfPresent(SafetyRiskRuleDO::getEnabled, reqVO.getEnabled())
                        .eqIfPresent(SafetyRiskRuleDO::getRiskLevel, reqVO.getRiskLevel())
                        .orderByDesc(SafetyRiskRuleDO::getId));
        // DO.conditionExpr → RespVO.condition（前端契约字段名）手动映射
        List<SafetyRiskRuleRespVO> list = BeanUtils.toBean(page.getList(), SafetyRiskRuleRespVO.class);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setCondition(page.getList().get(i).getConditionExpr());
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SafetyRiskRuleSaveReqVO reqVO) {
        SafetyRiskRuleDO rule = BeanUtils.toBean(reqVO, SafetyRiskRuleDO.class);
        // 属性名 conditionExpr（列 condition_expr，避 MySQL 保留字）与前端契约字段 condition 不同名，
        // BeanUtils 复制不到，手动映射
        rule.setConditionExpr(reqVO.getCondition());
        rule.setId(null);
        rule.setVersion(null);
        if (rule.getEnabled() == null) {
            rule.setEnabled(Boolean.TRUE); // 契约：新增缺省启用（前端开关默认开）
        }
        safetyRiskRuleMapper.insert(rule);
        return rule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SafetyRiskRuleSaveReqVO reqVO) {
        if (reqVO.getId() == null || reqVO.getVersion() == null) {
            // 与模块其他更新同口径：更新必须带主键与乐观锁版本，否则并发编辑会静默覆盖
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_RISK_RULE_DATA_MODIFIED);
        }
        loadRule(reqVO.getId());
        SafetyRiskRuleDO update = BeanUtils.toBean(reqVO, SafetyRiskRuleDO.class);
        update.setConditionExpr(reqVO.getCondition()); // 同 create：字段名不同，手动映射
        if (safetyRiskRuleMapper.updateById(update) == 0) { // @Version CAS
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_RISK_RULE_DATA_MODIFIED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Boolean enabled) {
        SafetyRiskRuleDO rule = loadRule(id);
        SafetyRiskRuleDO update = new SafetyRiskRuleDO();
        update.setId(rule.getId());
        update.setVersion(rule.getVersion());
        update.setEnabled(enabled == null ? Boolean.TRUE : enabled);
        if (safetyRiskRuleMapper.updateById(update) == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_RISK_RULE_DATA_MODIFIED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        loadRule(id);
        safetyRiskRuleMapper.deleteById(id); // @TableLogic 逻辑删除
    }

    private SafetyRiskRuleDO loadRule(Long id) {
        SafetyRiskRuleDO rule = safetyRiskRuleMapper.selectById(id);
        if (rule == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_RISK_RULE_NOT_EXISTS);
        }
        return rule;
    }
}
