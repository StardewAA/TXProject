package cn.iocoder.txgy.module.hrm.service.contract;

import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractTemplateSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractTemplateDO;

import java.util.List;

/**
 * HRM 合同模板 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmContractTemplateService {

    /**
     * 保存合同模板：有编号则更新，无编号则新增
     *
     * @param reqVO 模板信息
     * @return 模板编号
     */
    Long saveTemplate(HrmContractTemplateSaveReqVO reqVO);

    /**
     * 删除合同模板
     *
     * @param id 编号
     */
    void deleteTemplate(Long id);

    /**
     * 校验合同模板存在
     *
     * @param id 编号
     * @return 模板
     */
    HrmContractTemplateDO validateTemplateExists(Long id);

    /**
     * 获得合同模板列表
     *
     * @return 列表
     */
    List<HrmContractTemplateDO> getTemplateList();

    /**
     * 按分类获得合同模板列表
     *
     * @param category 模板分类
     * @return 列表
     */
    List<HrmContractTemplateDO> getTemplateListByCategory(Integer category);

    /**
     * 获得指定合同类型的启用模板（渲染使用）
     *
     * @param contractType 合同类型
     * @return 模板；不存在返回 null
     */
    HrmContractTemplateDO getEnabledTemplateByType(Integer contractType);

    /**
     * 获得租户默认启用模板（Offer 生成合同草稿时使用）
     *
     * @return 模板；不存在返回 null
     */
    HrmContractTemplateDO getDefaultEnabledTemplate();

}
