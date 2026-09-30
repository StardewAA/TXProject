package cn.iocoder.txgy.module.bidding.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 统一候选选项类型（GET /bidding/meta/options 的 type 取值）。
 *
 * <p>规范依据：《招投标模块开发规范》§4.4（菜单/权限码与接口同步）、§5 三-7（枚举以 code 存储）；
 * 用户口径 2026-09-15「统一接口」——把散落在各页面的「手输主键」收口成一套候选接口，
 * 前端下拉只认 {value,label,extra,disabled,status} 一种形状。
 *
 * <p>为什么需要它：内部主键（雪花 ID）不是给人看的，让运营手抄主键会产生"填错也保存成功"的数据污染
 * （证照挂到别的企业、证书挂到别的员工）。统一候选接口把"可选值"由后台驱动，前端不再硬编码也不需要用户输入。
 *
 * <p>注意：**不含 FILE**。fileId 必须来自 {@code POST /bidding/file/upload} 的响应
 * （见《11-文件上传与file_id契约-2026-09-10》"前端禁止让用户手填文件 ID"），
 * 文件没有"可枚举候选"语义，故不纳入本接口。
 */
public enum BiddingOptionTypeEnum {

    /** 企业主体（主体档案 bidding_subject_profile） */
    SUBJECT("企业主体"),
    /** 所属企业（租户自维护：本企业/子公司/分公司） */
    SUBJECT_COMPANY("所属企业"),
    /** 员工档案（bidding_personnel_profile） */
    PERSONNEL("员工"),
    /** 招投标项目（bidding_project） */
    PROJECT("项目"),
    /** 证照台账（certificate_info） */
    CERTIFICATE("证照"),
    /** 企业资质（enterprise_qualification） */
    QUALIFICATION("企业资质"),
    /** 安全生产许可证（qualification_safety_permit） */
    SAFETY_PERMIT("安全生产许可证"),
    /** 人员证书（personnel_certificate） */
    PERSONNEL_CERT("人员证书");

    private final String label;

    BiddingOptionTypeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 按 code 解析（忽略大小写与首尾空白）；无法识别返回 null，由调用方给出明确报错，不静默回落。 */
    public static BiddingOptionTypeEnum parse(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(v -> v.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /** 全部可用 code（用于报错文案，让调用方知道可选值而不是猜）。 */
    public static String codes() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining("/"));
    }
}
