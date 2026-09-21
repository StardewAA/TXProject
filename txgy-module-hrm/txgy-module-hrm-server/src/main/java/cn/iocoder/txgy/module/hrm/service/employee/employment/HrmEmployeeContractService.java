package cn.iocoder.txgy.module.hrm.service.employee.employment;

import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract.HrmEmployeeContractSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HRM 员工合同 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeContractService {

    /**
     * 创建员工合同
     *
     * @param reqVO 合同信息
     * @return 合同编号
     */
    Long createContract(@Valid HrmEmployeeContractSaveReqVO reqVO);

    /**
     * 更新员工合同
     *
     * @param reqVO 合同信息
     */
    void updateContract(@Valid HrmEmployeeContractSaveReqVO reqVO);

    /**
     * 删除员工合同
     *
     * @param id 合同编号
     */
    void deleteContract(Long id);

    /**
     * 校验员工合同是否存在
     *
     * @param id 合同编号
     * @return 员工合同
     */
    HrmEmployeeContractDO validateContractExists(Long id);

    /**
     * 获得员工合同列表
     *
     * @param employeeId 员工编号
     * @return 合同列表
     */
    List<HrmEmployeeContractDO> getContractListByEmployeeId(Long employeeId);

    /**
     * 获得指定月份到期合同的员工数量
     *
     * @param endTimes 合同结束时间范围
     * @return 员工数量
     */
    Long getExpireEmployeeCountInMonth(LocalDateTime[] endTimes);

    /**
     * 合同到期预警清单：在职员工 + 合同执行中 + 已开启到期提醒 + 到期时间在 warnDays 天内
     *
     * @param warnDays 提前预警天数
     * @return 即将到期的合同列表
     */
    List<HrmEmployeeContractDO> getExpireWarningList(int warnDays);

    /**
     * 发放 Offer 时生成劳务合同草稿（此时员工尚未建档，employeeId 为空）
     *
     * @param offer 录用 Offer
     * @return 合同编号；未配置模板时返回 null
     */
    Long createDraftForOffer(HrmRecruitOfferDO offer);

    /**
     * 员工建档后，把该 Offer 生成的合同草稿绑定到员工
     *
     * @param sourceOfferId 来源 Offer 编号
     * @param employeeId 员工编号
     * @param entryTime 入职时间，用于补齐合同起始时间
     */
    void bindEmployeeByOfferId(Long sourceOfferId, Long employeeId, LocalDateTime entryTime);

    /**
     * 按来源 Offer 查询合同
     *
     * @param sourceOfferId 来源 Offer 编号
     * @return 合同；不存在返回 null
     */
    HrmEmployeeContractDO getContractBySourceOfferId(Long sourceOfferId);

    /**
     * 记录已生成的合同文件（渲染后的 docx）
     *
     * @param id 合同编号
     * @param fileUrl 文件地址
     * @param remarkAppend 追加到备注的说明
     */
    void markGeneratedFile(Long id, String fileUrl, String remarkAppend);

    /**
     * 按用工形式切换合同模板（Offer 转员工时调用）
     *
     * <p>发 Offer 阶段用工形式还没定，只能用租户默认模板生成草稿；确认入职时已经选定用工形式，
     * 此时按该用工形式绑定的模板覆盖草稿的模板与合同类型，保证"签对合同"。</p>
     *
     * @param sourceOfferId 来源 Offer 编号
     * @param contractTemplateId 用工形式绑定的默认合同模板编号
     */
    void applyTemplateByEmploymentType(Long sourceOfferId, Long contractTemplateId);

}
