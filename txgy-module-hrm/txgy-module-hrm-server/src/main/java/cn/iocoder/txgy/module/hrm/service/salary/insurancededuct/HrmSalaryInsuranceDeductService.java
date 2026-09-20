package cn.iocoder.txgy.module.hrm.service.salary.insurancededuct;

import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.insurancededuct.HrmSalaryInsuranceDeductRespVO;

import java.util.List;

/**
 * HRM 社保代扣取数 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmSalaryInsuranceDeductService {

    /**
     * 单个员工的社保代扣金额
     */
    HrmSalaryInsuranceDeductRespVO getDeduct(Long employeeId, Integer year, Integer month);

    /**
     * 某月全部员工的社保代扣金额（月度工资表生成用）
     */
    List<HrmSalaryInsuranceDeductRespVO> getDeductListByMonth(Integer year, Integer month);

}
