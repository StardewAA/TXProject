package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 证照文件版本 Response VO（证照文件版本列表的**对外字段契约**）。
 *
 * <p>规范出处：《电子证照模块-前端接口契约-v1.0》§3.2「证照文件版本列表」列出的 16 个字段（F2 新增作废三要素）。
 *
 * <p>为什么这样写（设计遵循）：
 * <ul>
 *   <li><b>从 DO 直出改为 VO 收敛</b>：该端点原先直接返回 {@code List<CertFileVersionDO>}，把
 *       {@code tenantId}/{@code deleted}/{@code creator}/{@code updater} 等内部列一并吐给前端。
 *       这有两个后果：① 契约与实现之间没有稳定边界，改表就可能悄悄改接口；
 *       ② 前端可能无意依赖内部列，将来清理时变成破坏性变更。故显式声明对外字段集。</li>
 *   <li><b>字段名保持 {@code fileHash}，不改名为契约里写的 {@code sha256}</b>：契约 §3.2 文档化的
 *       {@code sha256} 与实现里的 {@code fileHash} 是同一个值（SHA-256 十六进制串）。此处选择"实现为准 + 契约更正"，
 *       因为给**已发布**的字段改名会让前端返工，收益为零（详见《后端变更通知-给前端.md》§4.1）。</li>
 *   <li><b>只暴露契约列出的字段</b>：{@code copyCreationTime}/{@code copyCreator}/{@code copyCause}/{@code remark}
 *       属于内部留痕（复制件制作信息与备注），本期契约未要求，故不外发；将来如需外发，应先更新契约再改本类。</li>
 * </ul>
 *
 * <p>业务目标：支撑前端"证照文件版本历史"列表与 §4.2「文件验真（指纹核验）」——前端需要用 {@code fileHash}
 * 与本地文件指纹比对，故该字段必须稳定、可预期地返回。
 *
 * @author txgy
 */
@Schema(description = "证照文件版本 Response VO")
@Data
public class CertFileVersionRespVO {

    @Schema(description = "文件版本主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "证照主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long certId;

    @Schema(description = "文件类别 code（字典 file_category）", example = "ORIGINAL")
    private String fileCategory;

    @Schema(description = "文件类别名称（字典 file_category 中文名；前端直接用，无需自行映射枚举）", example = "原件")
    private String fileCategoryName;

    @Schema(description = "文件名", example = "安全生产许可证.pdf")
    private String fileName;

    @Schema(description = "文件主键（文件服务返回）", example = "3072")
    private Long fileId;

    @Schema(description = "文件指纹（SHA-256 十六进制串）。契约 §3.2 曾写作 sha256，实际字段名以此为准", example = "9f2c…")
    private String fileHash;

    @Schema(description = "文件格式", example = "pdf")
    private String fileFormat;

    @Schema(description = "文件大小（字节）", example = "204800")
    private Long fileSize;

    @Schema(description = "版本号（同证照内自增，从 1 开始）", example = "1")
    private Integer version;

    @Schema(description = "是否当前版本（换版后旧版本置 false）", example = "true")
    private Boolean isCurrent;

    @Schema(description = "病毒扫描状态（统一口径 UNKNOWN/PENDING/CLEAN/INFECTED；INFECTED 不下发下载地址）", example = "CLEAN")
    private String virusStatus;

    @Schema(description = "是否可预览/下载（false=病毒未通过或已作废；前端据此禁用预览按钮，避免点了才报错）", example = "true")
    private Boolean downloadable;

    @Schema(description = "预览/下载地址（业务受控出口，浏览器内联预览；**不是**对象存储裸 url）。"
            + "可预览时返回 /admin-api/bidding/file/download?id={fileId}，否则为 null",
            example = "/admin-api/bidding/file/download?id=3072")
    private String previewUrl;

    @Schema(description = "复制件有效期至（可空；仅复制件类文件有）")
    private LocalDateTime copyExpiringTime;

    @Schema(description = "是否已作废（F2；作废行保留展示，列表需标灰/加'已作废'角标）", example = "false")
    private Boolean voided;

    @Schema(description = "作废原因（仅作废行非空）")
    private String voidReason;

    @Schema(description = "作废时间（仅作废行非空）")
    private LocalDateTime voidedAt;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * DO → VO。**手工逐字段映射**（不用反射拷贝）：这样"外发了哪些字段"在代码里一眼可见，
     * 新增 DO 字段时不会因为反射而**悄悄外发**——这正是本类存在的意义。
     */
    public static CertFileVersionRespVO from(CertFileVersionDO doObj) {
        return from(doObj, null);
    }

    /**
     * DO → VO（带字典解析器）。
     *
     * @param dictHelper 字典解析器；为 null 时中文名留空（前端回退显示 code）。
     *                   传 null 的场景：单测/无 Spring 上下文——中文名属展示增强，缺失不应导致失败。
     */
    public static CertFileVersionRespVO from(CertFileVersionDO doObj,
            cn.iocoder.txgy.module.bidding.enums.certificate.CertDictHelper dictHelper) {
        if (doObj == null) {
            return null;
        }
        CertFileVersionRespVO vo = new CertFileVersionRespVO();
        vo.setId(doObj.getId());
        vo.setCertId(doObj.getCertId());
        vo.setFileCategory(doObj.getFileCategory());
        vo.setFileName(doObj.getFileName());
        vo.setFileId(doObj.getFileId());
        vo.setFileHash(doObj.getFileHash());
        vo.setFileFormat(doObj.getFileFormat());
        vo.setFileSize(doObj.getFileSize());
        vo.setVersion(doObj.getVersion());
        vo.setIsCurrent(doObj.getIsCurrent());
        vo.setVirusStatus(doObj.getVirusStatus());
        // 中文名由字典解析（字典为唯一权威来源；解析失败/无解析器返回 null，前端回退显示 code）
        vo.setFileCategoryName(dictHelper == null ? null : dictHelper.label(
                cn.iocoder.txgy.module.bidding.enums.certificate.CertDictTypes.FILE_CATEGORY, doObj.getFileCategory()));
        // 可预览判定：病毒未通过或已作废的文件不给预览入口（前端据此禁用按钮，而非点了才报错）
        boolean virusOk = !"INFECTED".equals(doObj.getVirusStatus());
        boolean notVoided = !Boolean.TRUE.equals(doObj.getVoided());
        boolean canView = virusOk && notVoided && doObj.getFileId() != null;
        vo.setDownloadable(canView);
        // 受控预览地址：业务出口（非对象存储裸 url，红线 8）；不可预览时为 null
        vo.setPreviewUrl(canView ? "/admin-api/bidding/file/download?id=" + doObj.getFileId() : null);
        vo.setCopyExpiringTime(doObj.getCopyExpiringTime());
        vo.setVoided(doObj.getVoided());
        vo.setVoidReason(doObj.getVoidReason());
        vo.setVoidedAt(doObj.getVoidedAt());
        vo.setCreateTime(doObj.getCreateTime());
        return vo;
    }

    /** 列表转换（保持顺序）。 */
    public static List<CertFileVersionRespVO> fromList(List<CertFileVersionDO> list) {
        return fromList(list, null);
    }

    /** 列表转换（带字典解析器）。 */
    public static List<CertFileVersionRespVO> fromList(List<CertFileVersionDO> list,
            cn.iocoder.txgy.module.bidding.enums.certificate.CertDictHelper dictHelper) {
        return list == null ? List.of()
                : list.stream().map(doObj -> from(doObj, dictHelper)).toList();
    }

}
