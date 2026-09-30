package cn.iocoder.txgy.module.system.service.mail;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.system.controller.admin.mail.vo.account.MailAccountPageReqVO;
import cn.iocoder.txgy.module.system.controller.admin.mail.vo.account.MailAccountSaveReqVO;
import cn.iocoder.txgy.module.system.dal.dataobject.mail.MailAccountDO;
import cn.iocoder.txgy.module.system.dal.mysql.mail.MailAccountMapper;
import cn.iocoder.txgy.module.system.dal.redis.RedisKeyConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.system.enums.ErrorCodeConstants.MAIL_ACCOUNT_NOT_EXISTS;
import static cn.iocoder.txgy.module.system.enums.ErrorCodeConstants.MAIL_ACCOUNT_PASSWORD_REQUIRED;
import static cn.iocoder.txgy.module.system.enums.ErrorCodeConstants.MAIL_ACCOUNT_RELATE_TEMPLATE_EXISTS;

/**
 * 邮箱账号 Service 实现类
 *
 * @author wangjingyi
 * @since 2022-03-21
 */
@Service
@Validated
@Slf4j
public class MailAccountServiceImpl implements MailAccountService {

    @Resource
    private MailAccountMapper mailAccountMapper;

    @Resource
    private MailTemplateService mailTemplateService;

    @Override
    public Long createMailAccount(MailAccountSaveReqVO createReqVO) {
        // 新增时必须提供密码（授权码）：密码已不再随查询接口返回，前端无法回填
        if (StrUtil.isBlank(createReqVO.getPassword())) {
            throw exception(MAIL_ACCOUNT_PASSWORD_REQUIRED);
        }
        MailAccountDO account = BeanUtils.toBean(createReqVO, MailAccountDO.class);
        mailAccountMapper.insert(account);
        return account.getId();
    }

    @Override
    @CacheEvict(value = RedisKeyConstants.MAIL_ACCOUNT, key = "#updateReqVO.id")
    public void updateMailAccount(MailAccountSaveReqVO updateReqVO) {
        // 校验是否存在
        validateMailAccountExists(updateReqVO.getId());

        // 更新
        MailAccountDO updateObj = BeanUtils.toBean(updateReqVO, MailAccountDO.class);
        // 密码留空表示「不修改」：查询接口已不再返回密码，前端编辑时该字段必然为空。
        // 若不特殊处理，保存会把原密码整段清空，导致账号直接不可用。
        if (StrUtil.isBlank(updateReqVO.getPassword())) {
            updateObj.setPassword(null);
        } else {
            log.info("[updateMailAccount][账号({}) 密码已更新]", updateReqVO.getId());
        }
        mailAccountMapper.updateById(updateObj);
    }

    @Override
    @CacheEvict(value = RedisKeyConstants.MAIL_ACCOUNT, key = "#id")
    public void deleteMailAccount(Long id) {
        // 校验是否存在账号
        validateMailAccountExists(id);
        // 校验是否存在关联模版
        if (mailTemplateService.getMailTemplateCountByAccountId(id) > 0) {
            throw exception(MAIL_ACCOUNT_RELATE_TEMPLATE_EXISTS);
        }

        // 删除
        mailAccountMapper.deleteById(id);
    }

    @Override
    @CacheEvict(value = RedisKeyConstants.MAIL_ACCOUNT,
            allEntries = true) // allEntries 清空所有缓存，因为 Spring Cache 不支持按照 ids 批量删除
    public void deleteMailAccountList(List<Long> ids) {
        // 1. 校验是否存在关联模版
        for (Long id : ids) {
            if (mailTemplateService.getMailTemplateCountByAccountId(id) > 0) {
                throw exception(MAIL_ACCOUNT_RELATE_TEMPLATE_EXISTS);
            }
        }

        // 2. 批量删除
        mailAccountMapper.deleteByIds(ids);
    }

    private void validateMailAccountExists(Long id) {
        if (mailAccountMapper.selectById(id) == null) {
            throw exception(MAIL_ACCOUNT_NOT_EXISTS);
        }
    }

    @Override
    public MailAccountDO getMailAccount(Long id) {
        return mailAccountMapper.selectById(id);
    }

    @Override
    @Cacheable(value = RedisKeyConstants.MAIL_ACCOUNT, key = "#id", unless = "#result == null")
    public MailAccountDO getMailAccountFromCache(Long id) {
        return getMailAccount(id);
    }

    @Override
    public PageResult<MailAccountDO> getMailAccountPage(MailAccountPageReqVO pageReqVO) {
        return mailAccountMapper.selectPage(pageReqVO);
    }

    @Override
    public List<MailAccountDO> getMailAccountList() {
        return mailAccountMapper.selectList();
    }

}
