package cn.iocoder.txgy.module.crm.service.customer;

import cn.iocoder.txgy.module.crm.controller.admin.customer.vo.customer.CrmSupplierImportRespVO;

/**
 * CRM 供应商导入 Service 接口
 *
 * <p>从 Word（.docx）文档的表格中解析供应商信息，写入「上游供应商」客户库。
 *
 * @author 防腐保温项目组
 */
public interface CrmSupplierImportService {

    /**
     * 从 Word 文档导入供应商
     *
     * <p>解析规则：
     * 1. 只读取文档中的表格，首行作为表头，按表头名称识别列（供应商名称/联系人/联系电话/地址等）；
     * 2. 过滤空行、清洗空白字符、文档内按名称去重；
     * 3. 名称在系统中已存在时：{@code updateExisting=false} 跳过并计入重复；
     *    {@code updateExisting=true} 仅更新文档中有的字段，不覆盖其它信息，也不改变已有的客户类别。
     *
     * @param content        docx 文件内容
     * @param updateExisting 是否更新已存在的供应商
     * @param ownerUserId    导入供应商的负责人（用于数据权限）
     * @return 导入结果汇总
     */
    CrmSupplierImportRespVO importSupplierWord(byte[] content, boolean updateExisting, Long ownerUserId);

}
