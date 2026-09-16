package cn.iocoder.txgy.module.crm.service.contract;

import jakarta.validation.constraints.NotNull;

/**
 * CRM 合同 Word 生成 Service 接口
 *
 * 依据《采购合同-模板》《购销合同-模板》生成合同文档，
 * 保证输出格式严格贴合模板，且自动带入系统中已填写的合同信息。
 *
 * @author dhb52
 */
public interface CrmContractWordService {

    /**
     * 生成合同 Word 文档
     *
     * @param id 合同编号
     * @return docx 二进制内容
     */
    byte[] generateContractWord(@NotNull Long id);

}
