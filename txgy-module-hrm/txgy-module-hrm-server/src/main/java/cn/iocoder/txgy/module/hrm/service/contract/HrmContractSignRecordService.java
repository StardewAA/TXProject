package cn.iocoder.txgy.module.hrm.service.contract;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractSignRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractSignRecordSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractSignRecordDO;

import java.util.Collection;
import java.util.List;

/**
 * HRM 合同签署记录 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmContractSignRecordService {

    /**
     * 创建签署记录
     *
     * @param reqVO 签署记录信息
     * @return 记录编号
     */
    Long createSignRecord(HrmContractSignRecordSaveReqVO reqVO);

    /**
     * 幂等登记一条「待签」签署记录
     *
     * <p>转员工发送劳动合同时调用：合同已经生成并通知员工待签署，签署台账里必须同步出现这条记录，
     * 否则 HR 在「合同签署记录」里看不到、也无法催办与登记签署结果，合同就会一直停在「未执行」。</p>
     *
     * @param contractId 员工合同编号
     * @return 签署记录编号（该合同已存在待签记录时，直接返回原记录编号）
     */
    Long createPendingSignRecordIfAbsent(Long contractId);

    /**
     * 更新签署记录
     *
     * @param reqVO 签署记录信息
     */
    void updateSignRecord(HrmContractSignRecordSaveReqVO reqVO);

    /**
     * 删除签署记录
     *
     * @param id 记录编号
     */
    void deleteSignRecord(Long id);

    /**
     * 校验签署记录存在
     *
     * @param id 记录编号
     * @return 签署记录
     */
    HrmContractSignRecordDO validateSignRecordExists(Long id);

    /**
     * 获得签署记录分页
     *
     * @param reqVO 分页查询
     * @return 分页结果
     */
    PageResult<HrmContractSignRecordDO> getSignRecordPage(HrmContractSignRecordPageReqVO reqVO);

    /**
     * 获得指定合同的签署记录列表
     *
     * @param contractId 合同编号
     * @return 记录列表
     */
    List<HrmContractSignRecordDO> getSignRecordListByContractId(Long contractId);

    /**
     * 按合同编号批量获得签署记录（合同台账回填签署状态用）
     *
     * @param contractIds 合同编号集合
     * @return 签署记录列表（按编号倒序，最新的在前）
     */
    List<HrmContractSignRecordDO> getSignRecordListByContractIds(Collection<Long> contractIds);

    /**
     * 批量催办未签署的合同
     *
     * @param ids 签署记录编号列表
     * @return 实际催办成功的条数
     */
    int urgeSignRecords(List<Long> ids);

}
