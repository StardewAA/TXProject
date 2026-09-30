package cn.iocoder.txgy.module.hrm.service.employee.info;

import java.util.List;

/**
 * HRM 员工入职档案文档 Service 接口
 *
 * <p>行业场景：防腐保温项目部的档案是「纸质 + 电子」两套。工人进场时，项目部要一次性拿到
 * 个人信息档案、入职登记表、岗前培训协议三份表单；过去靠劳资员手工填。
 * 这里按模板自动生成员工专属文档（文档顶部注入已填好的员工信息），并归档到员工档案资料里。</p>
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeOnboardDocService {

    /**
     * 按模板生成员工的入职档案与培训协议文档，并归档到员工档案资料
     *
     * @param employeeId 员工编号
     * @return 本次新增归档的文档地址
     */
    List<String> generateOnboardDocs(Long employeeId);

}
