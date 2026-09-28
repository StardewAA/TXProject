package cn.iocoder.txgy.module.hrm.service.employee.archive;

/**
 * 员工档案自动补全 Service（问题 10：简历解析补全档案）
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeArchiveFillService {

    /**
     * 用简历结构化内容补全员工「教育经历 / 工作经历」。
     *
     * <p>在线简历（门户「求职资料」）会保存 {@code experiences}、{@code educationExperiences} 等结构化数组，
     * 入职建档时同步写入员工子档案，HR 不需要再手工敲一遍。</p>
     *
     * <p>幂等：员工已有教育/工作经历时不重复写入。</p>
     *
     * @param employeeId    员工编号
     * @param resumeContent 简历 JSON 文本（可为空）
     * @return 新写入的记录数
     */
    int fillFromResumeContent(Long employeeId, String resumeContent);

    /**
     * 取简历可解析文本：优先结构化内容；为空时尝试从附件（PDF）抽取文本。
     *
     * @param content 简历结构化内容
     * @param fileUrl 附件地址（可为空）
     * @return 可用文本；都拿不到时返回 null
     */
    String resolveResumeText(String content, String fileUrl);

    /**
     * 把从附件抽取到的文本回写到简历记录（便于后续 AI 简历分析/匹配使用），best-effort。
     *
     * @param resumeId 简历编号
     * @param text     抽取到的文本
     */
    void saveExtractedText(Long resumeId, String text);

    /**
     * 从简历文本里按规则提取档案字段（不依赖 AI，只做稳定识别，不做推断编造）
     *
     * @param resumeText 简历文本（结构化内容或附件抽取文本）
     * @return 提取结果；无可用字段时各项为空
     */
    cn.iocoder.txgy.module.hrm.util.HrmResumeRuleExtractor.Extracted extractProfile(String resumeText);

    /**
     * 把简历里识别到的证书写入员工证件列表（员工已有证件时跳过，避免重复）
     *
     * @param employeeId 员工编号
     * @param extracted  规则提取结果
     * @return 新增证件条数
     */
    int fillCertificatesFromResume(
            Long employeeId, cn.iocoder.txgy.module.hrm.util.HrmResumeRuleExtractor.Extracted extracted);

}
