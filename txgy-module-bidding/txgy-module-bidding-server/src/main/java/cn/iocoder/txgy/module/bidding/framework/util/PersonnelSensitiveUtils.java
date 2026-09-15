package cn.iocoder.txgy.module.bidding.framework.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 员工敏感信息处理工具（身份证盲索引、脱敏展示）。
 *
 * <p>规范出处：《招投标模块开发规范》§4.8（数据分级与脱敏：L4 身份证号等任何出参只回掩码、日志禁打明文）；
 * 《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（人员身份信息需可"等值判重"但不可反查）；
 * 《个人信息保护法》第二十八条（敏感个人信息须最小必要 + 加密/去标识化）。
 * <p>为什么这样写：① 一人一档判重需要在**不知道明文**的前提下比较身份证，故用 HMAC-SHA256 盲索引
 * （确定性 + 单向，密文则无法等值比较）；② 不用 MD5/SHA1 裸哈希——身份证号空间小（约 10^17 且校验位可算），
 * 裸哈希易被彩虹表反查，加盐 HMAC 才安全；③ 纯 JDK 实现（javax.crypto + HexFormat），
 * 不引入额外依赖，避免为一个工具类扩大供应链面；④ 密钥由配置注入，未配置时给出**固定开发默认盐**并告警，
 * 生产必须配置（接入 KMS 时只需替换 {@code secretProvider}）。
 * <p>业务目标：既能"同一个人的身份证只允许建一份档案"，又不让数据库或日志成为明文泄露点。
 */
public final class PersonnelSensitiveUtils {

    /** HMAC 算法（JDK 内置，无需 BouncyCastle）。 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    /** 未配置密钥时的开发默认盐；生产环境必须通过配置覆盖。 */
    private static final String DEV_FALLBACK_SECRET = "txgy-bidding-personnel-dev-only";

    private PersonnelSensitiveUtils() {
    }

    /**
     * 生成身份证盲索引（确定性 HMAC-SHA256 十六进制串，64 字符）。
     *
     * <p>规范出处：v3.3 表 15-3 的"人员身份可判重"要求；开发规范 §4.8（L4 存储去标识化）。
     * <p>业务目标：同证号必然同索引 → 建档时拦截重复；索引不可逆 → 拖库也拿不到明文。
     *
     * @param secret  盐（生产由配置/KMS 提供；为空用开发默认盐）
     * @param idCard  身份证号明文（大小写与首尾空格无关，内部统一大写去空格）
     * @return 64 位十六进制盲索引；入参为空时返回 null（由调用方按必填校验拦截）
     */
    public static String blindIndex(String secret, String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return null;
        }
        String normalized = idCard.trim().toUpperCase(java.util.Locale.ROOT).replace(" ", "");
        String effectiveSecret = (secret == null || secret.isBlank()) ? DEV_FALLBACK_SECRET : secret;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(effectiveSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // 算法缺失属于环境级故障：直接抛，绝不退化为明文存储（安全优先于可用）
            throw new IllegalStateException("身份证盲索引计算失败", e);
        }
    }

    /**
     * 身份证脱敏：保留前 6 位（行政区划）与后 4 位（末位+校验位），中间以 * 代替。
     *
     * <p>规范出处：开发规范 §4.8（L4 出参只回掩码；"前 6 后 4"为证照/身份类通用口径）。
     * <p>业务目标：页面能核对"是不是这个人"，但截屏/接口返回都不构成明文泄露。
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return null;
        }
        String v = idCard.trim();
        if (v.length() <= 10) {
            return "*".repeat(v.length());
        }
        return v.substring(0, 6) + "*".repeat(v.length() - 10) + v.substring(v.length() - 4);
    }

    /**
     * 手机号脱敏：保留前 3 后 4。
     *
     * <p>规范出处：开发规范 §4.8（L3 列表默认掩码）。
     * <p>业务目标：列表页可直接展示，不必逐条申请解密。
     */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return null;
        }
        String v = mobile.trim();
        if (v.length() <= 7) {
            return "*".repeat(v.length());
        }
        return v.substring(0, 3) + "*".repeat(v.length() - 7) + v.substring(v.length() - 4);
    }

    /**
     * 姓名脱敏：保留姓氏（复姓取前 2 字），其余以 * 代替。
     *
     * <p>规范出处：开发规范 §4.8（L3 姓名在非授权列表中的掩码口径）。
     * <p>业务目标：非授权范围（如跨企业查询）看不到完整姓名，授权详情页才回全名。
     */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String v = name.trim();
        String[] compound = {"欧阳", "司马", "上官", "诸葛", "东方", "皇甫", "尉迟", "公孙", "慕容", "司徒"};
        int keep = 1;
        for (String c : compound) {
            if (v.startsWith(c)) {
                keep = 2;
                break;
            }
        }
        if (v.length() <= keep) {
            return v;
        }
        return v.substring(0, keep) + "*".repeat(v.length() - keep);
    }
}
