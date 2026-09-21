package cn.iocoder.txgy.module.erp.service.finance;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.account.ErpAccountPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.account.ErpAccountSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.finance.ErpAccountDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.finance.ErpAccountRecordDO;
import cn.iocoder.txgy.module.erp.dal.mysql.finance.ErpAccountMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.finance.ErpAccountRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

/**
 * ERP 结算账户 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpAccountServiceImpl implements ErpAccountService {

    @Resource
    private ErpAccountMapper accountMapper;
    @Resource
    private ErpAccountRecordMapper accountRecordMapper;

    @Override
    public Long createAccount(ErpAccountSaveReqVO createReqVO) {
        // 插入
        ErpAccountDO account = BeanUtils.toBean(createReqVO, ErpAccountDO.class);
        if (account.getBalance() == null) {
            account.setBalance(BigDecimal.ZERO);
        }
        accountMapper.insert(account);
        // 返回
        return account.getId();
    }

    @Override
    public void updateAccount(ErpAccountSaveReqVO updateReqVO) {
        // 校验存在
        validateAccountExists(updateReqVO.getId());
        // 更新
        ErpAccountDO updateObj = BeanUtils.toBean(updateReqVO, ErpAccountDO.class);
        updateObj.setBalance(null); // 余额只允许通过业务记账变化
        accountMapper.updateById(updateObj);
    }

    @Override
    public void updateAccountDefaultStatus(Long id, Boolean defaultStatus) {
        // 1. 校验存在
        validateAccountExists(id);

        // 2.1 如果开启，则需要关闭所有其它的默认
        if (defaultStatus) {
            ErpAccountDO account = accountMapper.selectByDefaultStatus();
            if (account != null) {
                accountMapper.updateById(new ErpAccountDO().setId(account.getId()).setDefaultStatus(false));
            }
        }
        // 2.2 更新对应的默认状态
        accountMapper.updateById(new ErpAccountDO().setId(id).setDefaultStatus(defaultStatus));
    }

    @Override
    public void deleteAccount(Long id) {
        // 校验存在
        validateAccountExists(id);
        if (!accountRecordMapper.selectListByAccountId(id).isEmpty()) {
            throw exception(ACCOUNT_HAS_RECORDS);
        }
        // 删除
        accountMapper.deleteById(id);
    }

    private void validateAccountExists(Long id) {
        if (accountMapper.selectById(id) == null) {
            throw exception(ACCOUNT_NOT_EXISTS);
        }
    }

    @Override
    public ErpAccountDO getAccount(Long id) {
        return accountMapper.selectById(id);
    }

    @Override
    public ErpAccountDO validateAccount(Long id) {
        ErpAccountDO account = accountMapper.selectById(id);
        if (account == null) {
            throw exception(ACCOUNT_NOT_EXISTS);
        }
        if (CommonStatusEnum.isDisable(account.getStatus())) {
            throw exception(ACCOUNT_NOT_ENABLE, account.getName());
        }
        return account;
    }

    @Override
    public List<ErpAccountDO> getAccountListByStatus(Integer status) {
        return accountMapper.selectListByStatus(status);
    }

    @Override
    public List<ErpAccountDO> getAccountList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return accountMapper.selectByIds(ids);
    }

    @Override
    public PageResult<ErpAccountDO> getAccountPage(ErpAccountPageReqVO pageReqVO) {
        return accountMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordBalanceChange(Long accountId, String bizType, Long bizId, String bizNo,
                                    BigDecimal changeAmount, String remark) {
        ErpAccountDO account = accountMapper.selectByIdForUpdate(accountId);
        if (account == null) {
            throw exception(ACCOUNT_NOT_EXISTS);
        }
        if (CommonStatusEnum.isDisable(account.getStatus())) {
            throw exception(ACCOUNT_NOT_ENABLE, account.getName());
        }
        BigDecimal beforeBalance = ObjectUtil.defaultIfNull(account.getBalance(), BigDecimal.ZERO);
        accountMapper.addBalance(accountId, changeAmount);
        BigDecimal afterBalance = beforeBalance.add(changeAmount);
        accountRecordMapper.insert(new ErpAccountRecordDO().setAccountId(accountId)
                .setBizType(bizType).setBizId(bizId).setBizNo(bizNo)
                .setChangeAmount(changeAmount).setBeforeBalance(beforeBalance)
                .setAfterBalance(afterBalance).setRemark(remark));
    }

    @Override
    public List<ErpAccountRecordDO> getAccountRecordList(Long accountId) {
        validateAccountExists(accountId);
        return accountRecordMapper.selectListByAccountId(accountId);
    }

}
