package cn.iocoder.txgy.module.hrm.service.salary.piece;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.piece.HrmSalaryPieceRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.piece.HrmSalaryPieceRecordSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.piece.HrmSalaryPieceRecordDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.salary.piece.HrmSalaryPieceRecordMapper;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.SALARY_PIECE_RECORD_NOT_EXISTS;

/**
 * HRM 计件计量 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmSalaryPieceRecordServiceImpl implements HrmSalaryPieceRecordService {

    @Resource
    private HrmSalaryPieceRecordMapper pieceRecordMapper;
    @Resource
    private HrmEmployeeService employeeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePieceRecord(HrmSalaryPieceRecordSaveReqVO reqVO) {
        // 1. 校验员工存在
        HrmEmployeeDO employee = employeeService.getEmployee(reqVO.getEmployeeId());
        if (employee == null) {
            throw exception(EMPLOYEE_NOT_EXISTS);
        }
        if (reqVO.getId() != null && pieceRecordMapper.selectById(reqVO.getId()) == null) {
            throw exception(SALARY_PIECE_RECORD_NOT_EXISTS);
        }
        // 2. 金额一律由后端按「工程量 × 单价」计算，避免前端传错
        HrmSalaryPieceRecordDO record = BeanUtils.toBean(reqVO, HrmSalaryPieceRecordDO.class)
                .setQuantity(nvl(reqVO.getQuantity())).setUnitPrice(nvl(reqVO.getUnitPrice()))
                .setAmount(calculateAmount(reqVO.getQuantity(), reqVO.getUnitPrice()));
        pieceRecordMapper.insertOrUpdate(record);
        return record.getId();
    }

    @Override
    public void deletePieceRecord(Long id) {
        if (pieceRecordMapper.selectById(id) == null) {
            throw exception(SALARY_PIECE_RECORD_NOT_EXISTS);
        }
        pieceRecordMapper.deleteById(id);
    }

    @Override
    public PageResult<HrmSalaryPieceRecordDO> getPieceRecordPage(HrmSalaryPieceRecordPageReqVO reqVO) {
        return pieceRecordMapper.selectPage(reqVO);
    }

    @Override
    public List<HrmSalaryPieceRecordDO> getPieceRecordList(Long employeeId, Integer year, Integer month) {
        if (employeeId == null || year == null || month == null) {
            return Collections.emptyList();
        }
        return pieceRecordMapper.selectListByEmployeeIdAndMonth(employeeId, year, month);
    }

    @Override
    public Map<Long, BigDecimal> getPieceAmountMap(Collection<Long> employeeIds, Integer year, Integer month) {
        if (CollUtil.isEmpty(employeeIds) || year == null || month == null) {
            return Collections.emptyMap();
        }
        List<HrmSalaryPieceRecordDO> records =
                pieceRecordMapper.selectListByEmployeeIdsAndMonth(employeeIds, year, month);
        if (CollUtil.isEmpty(records)) {
            return Collections.emptyMap();
        }
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (HrmSalaryPieceRecordDO record : records) {
            result.merge(record.getEmployeeId(), nvl(record.getAmount()), BigDecimal::add);
        }
        return result;
    }

    /**
     * 计件金额 = 工程量 × 单价（保留 2 位、四舍五入）
     */
    private BigDecimal calculateAmount(BigDecimal quantity, BigDecimal unitPrice) {
        return nvl(quantity).multiply(nvl(unitPrice)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
