package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;

import java.time.LocalDate;

/**
 * HRM 证件台账 Service 接口
 *
 * 台账以 {@link HrmEmployeeCertificateDO} 为事实源，证件状态是派生数据，
 * 由 {@link HrmCertificateStatusCalculator} 按有效期与证件类型提前期计算，不做人工维护。
 *
 * @author OPENLAB BS
 */
public interface HrmCertificateLedgerService {

    /**
     * 创建证件台账记录
     *
     * @param reqVO 创建信息
     * @return 证件台账编号
     */
    Long createCertificateLedger(HrmCertificateLedgerSaveReqVO reqVO);

    /**
     * 更新证件台账记录
     *
     * @param reqVO 更新信息
     */
    void updateCertificateLedger(HrmCertificateLedgerSaveReqVO reqVO);

    /**
     * 删除证件台账记录
     *
     * @param id 证件台账编号
     */
    void deleteCertificateLedger(Long id);

    /**
     * 更新证件状态（仅支持注销与恢复）
     *
     * @param id     证件台账编号
     * @param status 目标状态
     */
    void updateCertificateLedgerStatus(Long id, Integer status);

    /**
     * 获得证件台账记录
     *
     * @param id 证件台账编号
     * @return 证件台账记录
     */
    HrmEmployeeCertificateDO getCertificateLedger(Long id);

    /**
     * 获得证件台账分页，含员工、证件类型、剩余天数与待处理预警数量
     *
     * @param reqVO 分页查询
     * @return 证件台账分页
     */
    PageResult<HrmCertificateLedgerRespVO> getCertificateLedgerPage(HrmCertificateLedgerPageReqVO reqVO);

    /**
     * 获得证件台账详情，含员工、证件类型等冗余字段
     *
     * @param id 证件台账编号
     * @return 证件台账详情
     */
    HrmCertificateLedgerRespVO getCertificateLedgerRespVO(Long id);

    /**
     * 校验证件台账记录存在，不存在时抛出异常
     *
     * @param id 证件台账编号
     * @return 证件台账记录
     */
    HrmEmployeeCertificateDO validateCertificateLedgerExists(Long id);

    /**
     * 全量刷新证件状态，供每日任务调用
     *
     * @param today 计算基准日期
     * @return 状态发生变更的证件数量
     */
    int refreshCertificateStatus(LocalDate today);

}
