package cn.iocoder.txgy.module.hrm.service.employee.info;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractTemplateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employment.HrmEmploymentTypeDO;
import cn.iocoder.txgy.module.hrm.enums.employee.info.HrmEmployeeFileTypeEnum;
import cn.iocoder.txgy.module.hrm.service.company.HrmCompanyProfileService;
import cn.iocoder.txgy.module.hrm.service.contract.HrmContractTemplateService;
import cn.iocoder.txgy.module.hrm.service.employment.HrmEmploymentTypeService;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.dept.dto.DeptRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HRM 员工入职档案文档 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
@Slf4j
public class HrmEmployeeOnboardDocServiceImpl implements HrmEmployeeOnboardDocService {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 默认生成的三份文档：模板名称关键字 → 归档到员工档案资料的类型
     *
     * <p>关键字对应用户上传的 22 份模板；找不到模板时跳过并记日志，不影响转员工。</p>
     */
    private static final List<TemplateBinding> DEFAULT_BINDINGS = List.of(
            new TemplateBinding("个人基本信息档案", HrmEmployeeFileTypeEnum.ARCHIVE_OTHER.getType()),
            new TemplateBinding("员工入职档案登记表", HrmEmployeeFileTypeEnum.ENTRY_REGISTRATION.getType()),
            new TemplateBinding("新员工入职", HrmEmployeeFileTypeEnum.TRAINING_AGREEMENT.getType()));

    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private HrmContractTemplateService contractTemplateService;
    @Resource
    private HrmCompanyProfileService companyProfileService;
    @Resource
    private HrmEmploymentTypeService employmentTypeService;
    @Resource
    private HrmEmployeeFileService employeeFileService;
    @Resource
    private FileApi fileApi;
    @Resource
    private DeptApi deptApi;

    @Override
    public List<String> generateOnboardDocs(Long employeeId) {
        HrmEmployeeDO employee = employeeService.validateEmployeeExists(employeeId);
        HrmCompanyProfileDO company = companyProfileService.getDefaultProfile();
        List<HrmContractTemplateDO> templates = contractTemplateService.getTemplateList();
        List<String> generated = new ArrayList<>();
        // 逐个模板生成，单个失败不影响其它文档与主流程
        for (TemplateBinding binding : DEFAULT_BINDINGS) {
        HrmContractTemplateDO template = templates.stream()
                    .filter(item -> StrUtil.contains(item.getName(), binding.nameKeyword()))
                    .findFirst().orElse(null);
            if (template == null || StrUtil.isBlank(template.getFileUrl())) {
                log.warn("[generateOnboardDocs][员工({}) 未找到模板({}*)，跳过]",
                        employeeId, binding.nameKeyword());
                continue;
            }
            // 幂等：同一员工 + 同一模板生成的文档名是固定的，已归档过就跳过，避免重复点击产生多份副本
            String fileName = buildFileName(template.getName(), employee);
            if (existsFile(employeeId, binding.fileType(), fileName)) {
                log.info("[generateOnboardDocs][员工({}) 已存在档案文档({})，跳过]", employeeId, fileName);
                continue;
            }
            try {
                byte[] content = fileApi.getFileContentByUrl(template.getFileUrl()).getCheckedData();
                if (content == null || content.length == 0) {
                    log.warn("[generateOnboardDocs][模板({})内容为空，跳过]", template.getName());
                    continue;
                }
                byte[] filled = injectEmployeeHeader(content, employee, company);
                String url = fileApi.createFile(filled, fileName, "hrm/employee/archive", DOCX_MIME);
                employeeFileService.appendEmployeeFiles(employeeId, binding.fileType(), Collections.singletonList(url));
                generated.add(url);
                log.info("[generateOnboardDocs][员工({}) 已生成档案文档({})，模板({})]",
                        employeeId, url, template.getName());
            } catch (Exception ex) {
                log.warn("[generateOnboardDocs][员工({}) 生成文档失败，模板({})]",
                        employeeId, template.getName(), ex);
            }
        }
        return generated;
    }

    /**
     * 判断该员工在该分类下是否已归档过同名文档（文件名带姓名+工号，可稳定标识同一份档案）
     */
    private boolean existsFile(Long employeeId, Integer fileType, String fileName) {
        return employeeFileService.getEmployeeFileList(employeeId).stream()
                .filter(file -> fileType.equals(file.getType()))
                .map(file -> resolveFileName(file.getUrl()))
                .anyMatch(fileName::equals);
    }

    /**
     * 从文件服务地址里解析出文件名（地址带百分号编码）
     */
    private String resolveFileName(String url) {
        if (StrUtil.isBlank(url)) {
            return "";
        }
        String path = StrUtil.subBefore(url, "?", false);
        String name = StrUtil.subAfter(path, "/", true);
        try {
            return java.net.URLDecoder.decode(name, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return name;
        }
    }

    /**
     * 在模板文档最前面注入「已填好的员工信息块」，让生成的文档一眼能看出属于谁
     */
    private byte[] injectEmployeeHeader(byte[] templateContent, HrmEmployeeDO employee,
                                        HrmCompanyProfileDO company) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(templateContent))) {
            XWPFParagraph first = CollUtil.isEmpty(document.getParagraphs())
                    ? null : document.getParagraphs().get(0);
            XWPFParagraph header = first != null
                    ? document.insertNewParagraph(first.getCTP().newCursor())
                    : document.createParagraph();
            XWPFRun run = header.createRun();
            run.setBold(true);
            run.setFontSize(10);
            run.setText(buildHeaderText(employee, company));
            if (first != null) {
                // 与模板正文之间留一个空行
                document.insertNewParagraph(first.getCTP().newCursor());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 组装信息块文本：单位、姓名、工号、用工形式、部门、岗位、入职日期、联系电话
     */
    private String buildHeaderText(HrmEmployeeDO employee, HrmCompanyProfileDO company) {
        String employmentTypeName = null;
        if (employee.getEmploymentTypeId() != null) {
            HrmEmploymentTypeDO employmentType =
                    employmentTypeService.getEmploymentType(employee.getEmploymentTypeId());
            employmentTypeName = employmentType != null ? employmentType.getName() : null;
        }
        String deptName = null;
        if (employee.getDeptId() != null) {
            DeptRespDTO dept = deptApi.getDept(employee.getDeptId()).getCheckedData();
            deptName = dept != null ? dept.getName() : null;
        }
        return StrUtil.format(
                "【系统自动生成 · 员工专属档案】单位：{}　姓名：{}　工号：{}　用工形式：{}　部门：{}　岗位：{}"
                        + "　　入职日期：{}　联系电话：{}　生成时间：{}",
                company != null ? company.getName() : "用工单位",
                StrUtil.blankToDefault(employee.getName(), "-"),
                StrUtil.blankToDefault(employee.getJobNumber(), "-"),
                StrUtil.blankToDefault(employmentTypeName, "-"),
                StrUtil.blankToDefault(deptName, "-"),
                StrUtil.blankToDefault(employee.getPostName(), "-"),
                employee.getEntryTime() != null ? employee.getEntryTime().format(DATE_FORMATTER) : "-",
                StrUtil.blankToDefault(employee.getMobile(), "-"),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
    }

    private String buildFileName(String templateName, HrmEmployeeDO employee) {
        return StrUtil.format("{}-{}-{}.docx", templateName,
                StrUtil.blankToDefault(employee.getName(), "员工"),
                StrUtil.blankToDefault(employee.getJobNumber(), String.valueOf(employee.getId())));
    }

    /**
     * 模板绑定：名称关键字 + 归档类型
     */
    private record TemplateBinding(String nameKeyword, Integer fileType) {
    }

}
