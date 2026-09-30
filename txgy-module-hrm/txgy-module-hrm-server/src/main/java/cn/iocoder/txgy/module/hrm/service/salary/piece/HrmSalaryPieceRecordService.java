package cn.iocoder.txgy.module.hrm.service.salary.piece;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.piece.HrmSalaryPieceRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.piece.HrmSalaryPieceRecordSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.piece.HrmSalaryPieceRecordDO;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * HRM 计件计量 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmSalaryPieceRecordService {

    /**
     * 保存计件计量明细（无编号新增、有编号修改），金额由后端按 工程量 × 单价 计算
     *
     * @param reqVO 明细
     * @return 明细编号
     */
    Long savePieceRecord(@Valid HrmSalaryPieceRecordSaveReqVO reqVO);

    /**
     * 删除计件计量明细
     *
     * @param id 明细编号
     */
    void deletePieceRecord(Long id);

    /**
     * 获得计件计量明细分页
     *
     * @param reqVO 分页条件
     * @return 分页结果
     */
    PageResult<HrmSalaryPieceRecordDO> getPieceRecordPage(@Valid HrmSalaryPieceRecordPageReqVO reqVO);

    /**
     * 获得某月某员工的计件明细
     *
     * @param employeeId 员工编号
     * @param year 年
     * @param month 月
     * @return 明细列表
     */
    List<HrmSalaryPieceRecordDO> getPieceRecordList(Long employeeId, Integer year, Integer month);

    /**
     * 汇总某月多个员工的计件金额（供工资表核算自动带入「计件工资」）
     *
     * @param employeeIds 员工编号集合
     * @param year 年
     * @param month 月
     * @return 员工编号 → 计件金额合计
     */
    Map<Long, BigDecimal> getPieceAmountMap(Collection<Long> employeeIds, Integer year, Integer month);

}
