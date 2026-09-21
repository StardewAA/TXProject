package cn.iocoder.txgy.module.hrm.service.contract;

/**
 * HRM 合同渲染 Service 接口
 *
 * <p>按合同模板渲染合同文件：模板中的 ${变量} 使用公司信息、员工档案、合同要素替换。</p>
 *
 * @author OPENLAB BS
 */
public interface HrmContractRenderService {

    /**
     * 渲染合同为 docx 字节内容
     *
     * @param contractId 合同编号
     * @return docx 内容
     */
    byte[] renderDocx(Long contractId);

    /**
     * 渲染后的文件名称
     *
     * @param contractId 合同编号
     * @return 文件名（不含路径）
     */
    String buildFileName(Long contractId);

}
