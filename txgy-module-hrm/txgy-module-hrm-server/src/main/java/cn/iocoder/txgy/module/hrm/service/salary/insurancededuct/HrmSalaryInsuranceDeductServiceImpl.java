package cn.iocoder.txgy.module.hrm.service.salary.insurancededuct;

import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.insurancededuct.HrmSalaryInsuranceDeductRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.insurance.monthrecord.HrmInsuranceMonthEmployeeRecordDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.insurance.monthrecord.HrmInsuranceMonthEmployeeRecordMapper;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HRM 社保代扣取数 Service 实现类
 *
 * <p>数据源：`hrm_insurance_month_employee_record`（月度社保员工记录）里的
 * `personal_insurance_amount`（个人社保）与 `personal_provident_fund_amount`（个人公积金）。</p>
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmSalaryInsuranceDeductServiceImpl implements HrmSalaryInsuranceDeductService {

    @Resource
    private HrmInsuranceMonthEmployeeRecordMapper monthEmployeeRecordMapper;
    @Resource
    private HrmEmployeeService employeeService;

    @Override
    public HrmSalaryInsuranceDeductRespVO getDeduct(Long employeeId, Integer year, Integer month) {
        if (employeeId == null || year == null || month == null) {
            return null;
        }
        HrmInsuranceMonthEmployeeRecordDO record =
                monthEmployeeRecordMapper.selectByEmployeeIdAndYearMonth(employeeId, year, month);
        Map<Long, HrmEmployeeDO> employeeMap = employeeService.getEmployeeMap(Set.of(employeeId));
        return build(employeeId, year, month, record, employeeMap.get(employeeId));
    }

    @Override
    public List<HrmSalaryInsuranceDeductRespVO> getDeductListByMonth(Integer year, Integer month) {
        if (year == null || month == null) {
            return List.of();
        }
        List<HrmInsuranceMonthEmployeeRecordDO> records =
                monthEmployeeRecordMapper.selectListByYearMonth(year, month);
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> employeeIds = records.stream().map(HrmInsuranceMonthEmployeeRecordDO::getEmployeeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, HrmEmployeeDO> employeeMap =
                employeeIds.isEmpty() ? Map.of() : employeeService.getEmployeeMap(employeeIds);
        return records.stream()
                .map(record -> build(record.getEmployeeId(), year, month, record,
                        employeeMap.get(record.getEmployeeId())))
                .collect(Collectors.toList());
    }

    private HrmSalaryInsuranceDeductRespVO build(Long employeeId, Integer year, Integer month,
                                                 HrmInsuranceMonthEmployeeRecordDO record,
                                                 HrmEmployeeDO employee) {
        HrmSalaryInsuranceDeductRespVO resp = new HrmSalaryInsuranceDeductRespVO();
        resp.setEmployeeId(employeeId);
        resp.setYear(year);
        resp.setMonth(month);
        if (employee != null) {
            resp.setEmployeeName(employee.getName());
            resp.setJobNumber(employee.getJobNumber());
        }
        BigDecimal insurance = record == null || record.getPersonalInsuranceAmount() == null
                ? BigDecimal.ZERO : record.getPersonalInsuranceAmount();
        BigDecimal fund = record == null || record.getPersonalProvidentFundAmount() == null
                ? BigDecimal.ZERO : record.getPersonalProvidentFundAmount();
        resp.setPersonalInsuranceAmount(insurance);
        resp.setPersonalProvidentFundAmount(fund);
        resp.setTotalDeductAmount(insurance.add(fund));
        resp.setFound(record != null);
        return resp;
    }

}
