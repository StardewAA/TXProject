package cn.iocoder.txgy.module.bidding.service.contract;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.contract.ContractDO;

/**
 * 工程合同服务（合同签署与项目完结）。
 *
 * <p>业务目标：登记中标后订立的书面合同，登记时校验履约保证金 ≤ 合同价 10%（条例第57条）
 * 与签约 ≤ 中标通知书发出 30 日（招标投标法第46条）。
 */
public interface ContractService {

    /** 登记合同（含履约保证金 10% + 签约 30 日校验）。 */
    Long save(ContractSaveReqVO reqVO);

    /** 按主键查询。 */
    ContractDO get(Long id);

    /** 分页查询。 */
    PageResult<ContractDO> page(ContractPageReqVO reqVO);

    /**
     * 合同归档（SIGNED→ARCHIVED，幂等）：落归档时间 + 保存到期（归档 + 15 年，自动计算）。
     * 归档后只读锁定（电子办法技术规范 存档≥15 年；说明书 §11.3 第 5 条）。
     *
     * @param id 合同主键
     */
    void archive(Long id);

    /**
     * 刷新保存期限提醒标记（自动计算）：距到期 ≤90 天 → EXPIRING_SOON，已到期 → EXPIRED。
     * 惰性触发（get/page 时对归档行计算落库）。
     *
     * @param contract 合同记录
     */
    void refreshRetentionWarn(ContractDO contract);
}
