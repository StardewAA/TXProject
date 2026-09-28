package cn.iocoder.txgy.module.hrm.service.employee.archive;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.educationexperience.HrmEmployeeEducationExperienceSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.certificate.HrmEmployeeCertificateSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.workexperience.HrmEmployeeWorkExperienceSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.resume.HrmRecruitResumeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.resume.HrmRecruitResumeMapper;
import cn.iocoder.txgy.module.hrm.service.employee.experience.HrmEmployeeEducationExperienceService;
import cn.iocoder.txgy.module.hrm.service.employee.experience.HrmEmployeeWorkExperienceService;
import cn.iocoder.txgy.module.hrm.util.HrmOnboardFillUtils;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * 员工档案自动补全 Service 实现（问题 10）
 *
 * <p>简历来源有两类：</p>
 * <ol>
 *   <li><b>在线简历</b>：{@code content} 是结构化 JSON（experiences / educationExperiences），可直接映射成员工子档案；</li>
 *   <li><b>附件简历（PDF）</b>：{@code content} 为空，这里用 PDFBox 抽取文本回写到 content，
 *       一方面让「HR 一键查看/后续 AI 分析」有内容可用，另一方面便于人工核对。</li>
 * </ol>
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class HrmEmployeeArchiveFillServiceImpl implements HrmEmployeeArchiveFillService {

    /**
     * 回写简历文本的最大长度，避免把整本简历塞爆 DB 字段
     */
    private static final int MAX_RESUME_TEXT_LENGTH = 20_000;

    @Resource
    private HrmEmployeeEducationExperienceService educationExperienceService;
    @Resource
    private HrmEmployeeWorkExperienceService workExperienceService;
    @Resource
    private FileApi fileApi;
    @Resource
    private HrmRecruitResumeMapper recruitResumeMapper;
    @Resource
    private cn.iocoder.txgy.module.hrm.service.employee.experience.HrmEmployeeCertificateService certificateService;

    @Override
    public int fillFromResumeContent(Long employeeId, String resumeContent) {
        if (employeeId == null || StrUtil.isBlank(resumeContent)) {
            return 0;
        }
        JSONObject root;
        try {
            root = JSONUtil.parseObj(resumeContent);
        } catch (Throwable ex) {
            log.debug("[fillFromResumeContent][员工({})的简历不是 JSON，跳过结构化补全]", employeeId);
            return 0;
        }
        // 幂等：已有记录时不重复写入（HR 手工维护过的以 HR 为准）
        boolean hasEducation = CollUtil.isNotEmpty(
                educationExperienceService.getEducationExperienceListByEmployeeId(employeeId));
        boolean hasWork = CollUtil.isNotEmpty(
                workExperienceService.getWorkExperienceListByEmployeeId(employeeId));
        int filled = 0;
        if (!hasEducation) {
            filled += fillEducationExperiences(employeeId, root.getJSONArray("educationExperiences"));
        }
        if (!hasWork) {
            filled += fillWorkExperiences(employeeId, root.getJSONArray("experiences"));
        }
        return filled;
    }

    private int fillEducationExperiences(Long employeeId, JSONArray array) {
        if (array == null || array.isEmpty()) {
            return 0;
        }
        int created = 0;
        int sort = 1;
        for (Object item : array) {
            if (!(item instanceof JSONObject row)) {
                continue;
            }
            String school = row.getStr("school");
            if (StrUtil.isBlank(school)) {
                continue;
            }
            Integer education = HrmOnboardFillUtils.mapEducationTextToEmployeeEducation(row.getStr("degree"));
            try {
                HrmEmployeeEducationExperienceSaveReqVO reqVO = new HrmEmployeeEducationExperienceSaveReqVO();
                reqVO.setEmployeeId(employeeId);
                reqVO.setEducation(education == null ? 8 : education);
                reqVO.setGraduateSchool(school);
                reqVO.setMajor(StrUtil.blankToDefault(row.getStr("major"), "未填写"));
                reqVO.setAdmissionTime(HrmOnboardFillUtils.parseResumeDate(row.getStr("start"), false));
                reqVO.setGraduationTime(HrmOnboardFillUtils.parseResumeDate(row.getStr("end"), true));
                reqVO.setTeachingMethods(resolveTeachingMethods(row.getStr("schoolType")));
                reqVO.setFirstDegree(created == 0);
                reqVO.setSort(sort++);
                educationExperienceService.createEducationExperience(reqVO);
                created++;
            } catch (Throwable ex) {
                log.warn("[fillEducationExperiences][员工({})写入教育经历失败]", employeeId, ex);
            }
        }
        return created;
    }

    private int fillWorkExperiences(Long employeeId, JSONArray array) {
        if (array == null || array.isEmpty()) {
            return 0;
        }
        int created = 0;
        int sort = 1;
        for (Object item : array) {
            if (!(item instanceof JSONObject row)) {
                continue;
            }
            String company = row.getStr("company");
            if (StrUtil.isBlank(company)) {
                continue;
            }
            String remark = StrUtil.join("；", StrUtil.blankToDefault(row.getStr("description"), ""),
                    StrUtil.blankToDefault(row.getStr("achievements"), ""));
            try {
                HrmEmployeeWorkExperienceSaveReqVO reqVO = new HrmEmployeeWorkExperienceSaveReqVO();
                reqVO.setEmployeeId(employeeId);
                reqVO.setWorkUnit(company);
                reqVO.setPostName(StrUtil.blankToDefault(row.getStr("position"), "未填写"));
                reqVO.setStartTime(HrmOnboardFillUtils.parseResumeDate(row.getStr("start"), false));
                reqVO.setEndTime(HrmOnboardFillUtils.parseResumeDate(row.getStr("end"), true));
                reqVO.setRemark(StrUtil.maxLength(remark, 400));
                reqVO.setSort(sort++);
                workExperienceService.createWorkExperience(reqVO);
                created++;
            } catch (Throwable ex) {
                log.warn("[fillWorkExperiences][员工({})写入工作经历失败]", employeeId, ex);
            }
        }
        return created;
    }

    private Integer resolveTeachingMethods(String schoolType) {
        if (StrUtil.isBlank(schoolType)) {
            return 1;
        }
        if (schoolType.contains("成人")) {
            return 2;
        }
        if (schoolType.contains("远程") || schoolType.contains("网络")) {
            return 3;
        }
        if (schoolType.contains("自学")) {
            return 4;
        }
        if (schoolType.contains("全日制")) {
            return 1;
        }
        return 5;
    }

    @Override
    public String resolveResumeText(String content, String fileUrl) {
        if (StrUtil.isNotBlank(content)) {
            return content;
        }
        if (StrUtil.isBlank(fileUrl)) {
            return null;
        }
        // 只处理 PDF：其它格式（doc/docx）暂不做文本抽取，交给人工或后续 AI 解析
        if (!fileUrl.toLowerCase().contains(".pdf")) {
            return null;
        }
        try {
            byte[] bytes = fileApi.getFileContentByUrl(fileUrl).getCheckedData();
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            // 模块内 PDFBox 为 2.x（与 Offer PDF 生成保持一致），使用 PDDocument.load(InputStream)
            try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes))) {
                String text = new PDFTextStripper().getText(document);
                text = StrUtil.trim(text);
                if (StrUtil.isBlank(text)) {
                    return null;
                }
                return StrUtil.maxLength(text, MAX_RESUME_TEXT_LENGTH);
            }
        } catch (Throwable ex) {
            log.warn("[resolveResumeText][附件简历({})文本抽取失败]", fileUrl, ex);
            return null;
        }
    }

    @Override
    public void saveExtractedText(Long resumeId, String text) {
        if (resumeId == null || StrUtil.isBlank(text)) {
            return;
        }
        try {
            HrmRecruitResumeDO resume = recruitResumeMapper.selectById(resumeId);
            if (resume == null || StrUtil.isNotBlank(resume.getContent())) {
                return;
            }
            resume.setContent(StrUtil.maxLength(text, MAX_RESUME_TEXT_LENGTH));
            resume.setRemark(StrUtil.blankToDefault(resume.getRemark(), "")
                    + "【系统】已从附件抽取文本，便于后续简历分析与档案补全");
            recruitResumeMapper.updateById(resume);
        } catch (Throwable ex) {
            log.warn("[saveExtractedText][简历({})回写抽取文本失败]", resumeId, ex);
        }
    }

    @Override
    public cn.iocoder.txgy.module.hrm.util.HrmResumeRuleExtractor.Extracted extractProfile(String resumeText) {
        return cn.iocoder.txgy.module.hrm.util.HrmResumeRuleExtractor.extract(resumeText);
    }

    @Override
    public int fillCertificatesFromResume(
            Long employeeId, cn.iocoder.txgy.module.hrm.util.HrmResumeRuleExtractor.Extracted extracted) {
        if (employeeId == null || extracted == null || CollUtil.isEmpty(extracted.getCertificates())) {
            return 0;
        }
        // 员工已有证件时不重复写入（HR 维护过的以 HR 为准）
        if (CollUtil.isNotEmpty(certificateService.getCertificateListByEmployeeId(employeeId))) {
            return 0;
        }
        int created = 0;
        for (String name : extracted.getCertificates()) {
            try {
                HrmEmployeeCertificateSaveReqVO reqVO = new HrmEmployeeCertificateSaveReqVO();
                reqVO.setEmployeeId(employeeId);
                reqVO.setName(name);
                reqVO.setRemark("系统按简历识别，请在「资格证件」中补全证件类型/编号/有效期");
                reqVO.setSort(created + 1);
                certificateService.createCertificate(reqVO);
                created++;
            } catch (Throwable ex) {
                log.warn("[fillCertificatesFromResume][员工({})写入证件「{}」失败]", employeeId, name, ex);
            }
        }
        return created;
    }

}
