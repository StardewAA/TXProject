package cn.iocoder.txgy.module.hrm.service.contract;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractTemplateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeSalaryCardDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.service.company.HrmCompanyProfileService;
import cn.iocoder.txgy.module.hrm.service.employee.employment.HrmEmployeeContractService;
import cn.iocoder.txgy.module.hrm.service.employee.employment.HrmEmployeeSalaryCardService;
import cn.iocoder.txgy.module.hrm.service.employee.experience.HrmEmployeeCertificateService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CONTRACT_TEMPLATE_FILE_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CONTRACT_TEMPLATE_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CONTRACT_RENDER_COMPANY_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CONTRACT_RENDER_DATA_REQUIRED;

/**
 * HRM 合同渲染 Service 实现类
 *
 * <p>渲染规则：模板中的 ${变量} 使用公司信息 + 员工档案 + 合同要素替换；除合同主体信息外，
 * 缺失的变量替换为空字符串，不阻断导出。</p>
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class HrmContractRenderServiceImpl implements HrmContractRenderService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private HrmEmployeeContractService contractService;
    @Resource
    private HrmContractTemplateService contractTemplateService;
    @Resource
    private HrmCompanyProfileService companyProfileService;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private HrmEmployeeSalaryCardService salaryCardService;
    @Resource
    private HrmEmployeeCertificateService certificateService;
    @Resource
    private FileApi fileApi;

    @Override
    public byte[] renderDocx(Long contractId) {
        // 1. 校验合同与模板
        HrmEmployeeContractDO contract = contractService.validateContractExists(contractId);
        HrmContractTemplateDO template = resolveTemplate(contract);
        if (StrUtil.isBlank(template.getFileUrl())) {
            throw exception(CONTRACT_TEMPLATE_NOT_EXISTS);
        }

        // 2. 准备变量
        Map<String, String> variables = buildVariables(contract);

        // 3. 读取模板并渲染
        byte[] templateContent = fileApi.getFileContentByUrl(template.getFileUrl()).getCheckedData();
        if (templateContent == null || templateContent.length == 0) {
            throw exception(CONTRACT_TEMPLATE_FILE_NOT_EXISTS);
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(templateContent))) {
            replaceInParagraphs(document.getParagraphs(), variables);
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        replaceInParagraphs(cell.getParagraphs(), variables);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("[renderDocx][合同({}) 渲染失败]", contractId, e);
            throw exception(CONTRACT_TEMPLATE_FILE_NOT_EXISTS);
        }
    }

    @Override
    public String buildFileName(Long contractId) {
        HrmEmployeeContractDO contract = contractService.validateContractExists(contractId);
        HrmEmployeeDO employee = employeeService.validateEmployeeExists(contract.getEmployeeId());
        // 命名规则（已确认）：员工姓名-合同类型.docx，例：孙莉-固定期限劳动合同.docx
        String typeName = java.util.Arrays.stream(
                        cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractTypeEnum.values())
                .filter(item -> item.getType().equals(contract.getType()))
                .map(cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractTypeEnum::getName)
                .findFirst().orElse("劳动合同");
        return StrUtil.format("{}-{}.docx", StrUtil.blankToDefault(employee.getName(), "员工"), typeName);
    }

    /**
     * 选择模板：合同指定模板 > 按合同类型取启用模板
     */
    private HrmContractTemplateDO resolveTemplate(HrmEmployeeContractDO contract) {
        if (contract.getTemplateId() != null) {
            return contractTemplateService.validateTemplateExists(contract.getTemplateId());
        }
        HrmContractTemplateDO template =
                contractTemplateService.getEnabledTemplateByType(contract.getType());
        if (template == null) {
            throw exception(CONTRACT_TEMPLATE_NOT_EXISTS);
        }
        return template;
    }

    /**
     * 组装模板变量
     */
    private Map<String, String> buildVariables(HrmEmployeeContractDO contract) {
        // 1. 公司信息（合同甲方）：优先合同上的签约公司名称，其余取默认用工主体
        HrmCompanyProfileDO company = companyProfileService.getDefaultProfile();
        String companyName = StrUtil.blankToDefault(contract.getSignCompany(),
                company != null ? company.getName() : null);
        if (StrUtil.isBlank(companyName)) {
            throw exception(CONTRACT_RENDER_COMPANY_REQUIRED);
        }

        // 2. 员工信息（合同乙方）
        HrmEmployeeDO employee = employeeService.validateEmployeeExists(contract.getEmployeeId());
        if (StrUtil.isBlank(employee.getName())) {
            throw exception(CONTRACT_RENDER_DATA_REQUIRED);
        }

        Map<String, String> variables = new HashMap<>();
        // 2.1 公司（甲方）
        variables.put("companyName", companyName);
        variables.put("companyCreditCode", company != null ? nullToEmpty(company.getCreditCode()) : "");
        variables.put("companyAddress", company != null ? nullToEmpty(company.getAddress()) : "");
        variables.put("companyLegalPerson", company != null ? nullToEmpty(company.getLegalPerson()) : "");
        variables.put("companyPhone", company != null ? nullToEmpty(company.getPhone()) : "");
        variables.put("commercialInsurance", StrUtil.blankToDefault(contract.getCommercialInsurance(),
                company != null ? company.getCommercialInsurance() : null));

        // 2.2 员工（乙方）
        variables.put("employeeName", nullToEmpty(employee.getName()));
        variables.put("employeeIdNumber", nullToEmpty(employee.getIdNumber()));
        variables.put("employeeAddress", nullToEmpty(employee.getAddress()));
        variables.put("employeePhone", nullToEmpty(employee.getMobile()));

        // 2.3 收款账户（员工工资卡）
        HrmEmployeeSalaryCardDO salaryCard = salaryCardService.getSalaryCardByEmployeeId(employee.getId());
        variables.put("bankAccount", salaryCard != null ? nullToEmpty(salaryCard.getBankCardNumber()) : "");
        variables.put("bankName", salaryCard != null
                ? StrUtil.blankToDefault(salaryCard.getBankBranchName(), salaryCard.getBankName()) : "");

        // 2.4 岗位与地点
        variables.put("jobType", nullToEmpty(employee.getPostName()));
        variables.put("jobContent", nullToEmpty(contract.getWorkContent()));
        variables.put("workLocation", StrUtil.blankToDefault(contract.getWorkLocation(),
                employee.getWorkAddress()));
        variables.put("contractTask", nullToEmpty(contract.getWorkTask()));

        // 2.5 期限与计酬
        variables.put("contractStartDate", formatDate(contract.getStartTime()));
        variables.put("contractEndDate", formatDate(contract.getEndTime()));
        variables.put("payMode", formatPayMode(contract.getPayMode()));
        variables.put("payAmount", contract.getPayAmount() == null ? ""
                : contract.getPayAmount().stripTrailingZeros().toPlainString());
        variables.put("payUnit", nullToEmpty(contract.getPayUnit()));
        variables.put("payDate", contract.getPayDay() == null ? "" : String.valueOf(contract.getPayDay()));

        // 2.6 保险：有社保方案摘要则带入，否则给通用表述
        variables.put("insuranceSummary", "按国家及当地社会保险政策执行，详见社保方案");

        // 2.7 特种作业证件（取最新一条）
        List<HrmEmployeeCertificateDO> certificates =
                certificateService.getCertificateListByEmployeeId(employee.getId());
        HrmEmployeeCertificateDO certificate = certificates == null || certificates.isEmpty()
                ? null : certificates.get(certificates.size() - 1);
        variables.put("certificateName", certificate != null ? nullToEmpty(certificate.getName()) : "");
        variables.put("certificateNo", certificate != null ? nullToEmpty(certificate.getNo()) : "");
        variables.put("certificateExpire", certificate != null ? formatDate(certificate.getEndTime()) : "");

        // 2.8 其他
        variables.put("attendanceMode", StrUtil.blankToDefault(contract.getAttendanceMode(), "人脸/定位打卡"));
        variables.put("contractNo", StrUtil.blankToDefault(contract.getNo(), "HT" + contract.getId()));
        variables.put("signDate", formatDate(contract.getSignTime() != null
                ? contract.getSignTime() : LocalDateTime.now()));
        return variables;
    }

    /**
     * 替换段落中的占位符；占位符可能被拆分在多个 run 中，这里按段落合并处理
     */
    private void replaceInParagraphs(List<XWPFParagraph> paragraphs, Map<String, String> variables) {
        for (XWPFParagraph paragraph : paragraphs) {
            String text = paragraph.getText();
            if (StrUtil.isBlank(text) || !text.contains("${")) {
                continue;
            }
            String replaced = replace(text, variables);
            List<XWPFRun> runs = paragraph.getRuns();
            if (runs.isEmpty()) {
                paragraph.createRun().setText(replaced);
                continue;
            }
            // 第一个 run 承载全部文本，其余 run 清空，避免样式错乱
            runs.get(0).setText(replaced, 0);
            for (int i = 1; i < runs.size(); i++) {
                runs.get(i).setText("", 0);
            }
        }
    }

    private String replace(String text, Map<String, String> variables) {
        String result = text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", nullToEmpty(entry.getValue()));
        }
        // 未提供取值的占位符替换为空，避免导出带 ${} 的文件
        return result.replaceAll("\\$\\{[A-Za-z0-9_]+}", "");
    }

    private String formatDate(LocalDateTime time) {
        return time == null ? "" : time.toLocalDate().format(DATE_FORMATTER);
    }

    private String formatPayMode(Integer payMode) {
        if (payMode == null) {
            return "";
        }
        return switch (payMode) {
            case 1 -> "月薪";
            case 2 -> "日薪";
            case 3 -> "计件";
            default -> "";
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

}
