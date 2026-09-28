package cn.iocoder.txgy.module.hrm.service.contract;

import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractTemplateSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractTemplateDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.contract.HrmContractTemplateMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CONTRACT_TEMPLATE_NOT_EXISTS;

/**
 * HRM 合同模板 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmContractTemplateServiceImpl implements HrmContractTemplateService {

    @Resource
    private HrmContractTemplateMapper contractTemplateMapper;

    @Override
    public Long saveTemplate(HrmContractTemplateSaveReqVO reqVO) {
        HrmContractTemplateDO template = BeanUtils.toBean(reqVO, HrmContractTemplateDO.class);
        if (template.getVersion() == null) {
            template.setVersion(1);
        }
        if (template.getStatus() == null) {
            template.setStatus(0);
        }
        if (reqVO.getId() == null) {
            // 1.1 新增：记录首版变更信息
            template.setChangeUserId(getLoginUserId()).setChangeTime(java.time.LocalDateTime.now());
            contractTemplateMapper.insert(template);
        } else {
            // 1.2 更新：模板文件不允许静默覆盖，文件变更时自动升版本并留痕
            HrmContractTemplateDO oldTemplate = validateTemplateExists(reqVO.getId());
            boolean fileChanged = !java.util.Objects.equals(oldTemplate.getFileUrl(), template.getFileUrl());
            if (fileChanged) {
                template.setVersion((oldTemplate.getVersion() == null ? 1 : oldTemplate.getVersion()) + 1);
            }
            template.setChangeUserId(getLoginUserId()).setChangeTime(java.time.LocalDateTime.now());
            contractTemplateMapper.updateById(template);
        }
        // 2. 默认模板唯一：同一「合同类型 + 适用用工形式」下只允许一个默认模板
        if (Boolean.TRUE.equals(reqVO.getDefaultStatus())) {
            contractTemplateMapper.selectList().stream()
                    .filter(item -> !item.getId().equals(template.getId()))
                    .filter(item -> java.util.Objects.equals(item.getContractType(), template.getContractType()))
                    .filter(item -> java.util.Objects.equals(item.getEmployType(), template.getEmployType()))
                    .filter(item -> Boolean.TRUE.equals(item.getDefaultStatus()))
                    .forEach(item -> contractTemplateMapper.updateById(
                            new HrmContractTemplateDO().setId(item.getId()).setDefaultStatus(false)));
        }
        return template.getId();
    }

    @Override
    public void deleteTemplate(Long id) {
        validateTemplateExists(id);
        contractTemplateMapper.deleteById(id);
    }

    @Override
    public HrmContractTemplateDO validateTemplateExists(Long id) {
        HrmContractTemplateDO template = contractTemplateMapper.selectById(id);
        if (template == null) {
            throw exception(CONTRACT_TEMPLATE_NOT_EXISTS);
        }
        return template;
    }

    @Override
    public List<HrmContractTemplateDO> getTemplateList() {
        return contractTemplateMapper.selectList();
    }

    @Override
    public List<HrmContractTemplateDO> getTemplateListByCategory(Integer category) {
        return contractTemplateMapper.selectListByCategory(category);
    }

    @Override
    public HrmContractTemplateDO getEnabledTemplateByType(Integer contractType) {
        return contractTemplateMapper.selectEnabledByType(contractType);
    }

    @Override
    public HrmContractTemplateDO getDefaultEnabledTemplate() {
        return contractTemplateMapper.selectDefaultEnabled();
    }

}
