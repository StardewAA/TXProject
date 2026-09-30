package cn.iocoder.txgy.module.bidding.service.subject;
// [依据] GB 32100-2015《法人和其他组织统一社会信用代码编码规则》：18 位 = 登记管理部门代码1位 + 机构类别代码1位
//       + 登记管理机关行政区划码6位 + 主体标识码(组织机构代码)9位 + 校验码1位。
//       校验：前 17 位字符（字符集不含 I/O/S/V/Z）按加权因子 [1,3,9,27,19,26,16,17,20,29,25,13,8,24,10,30,28] 加权和
//       mod 31，余数对应字符序（0-9, A-H,J-N,P-R,T-U,W-Y，共 31 字符）即校验位；标准全文见国家标准 GB 32100-2015
//       （全国标准信息公共服务平台 std.samr.gov.cn；本工具为实现性摘录）。
import org.springframework.util.StringUtils;

/**
 * 统一社会信用代码（GB 32100-2015）校验工具：字符集 + 校验码（ISO 7064 MOD 31,37 变形算法按 GB32100 附表）。
 */
public final class UsccUtils {

    /** GB 32100-2015 允许字符集（按标准字符表，不含 I、O、S、V、Z） */
    private static final String CHARSET = "0123456789ABCDEFGHJKLMNPQRTUWXY";
    /** 前 17 位加权因子（GB 32100-2015 附录） */
    private static final int[] WEIGHTS = {1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28};
    private static final int MOD = 31;

    private UsccUtils() {
    }

    /**
     * 校验统一社会信用代码（null/空/长度非 18/非法字符/校验码不符均判 false）。
     */
    public static boolean isValid(String code) {
        if (!StringUtils.hasText(code) || code.length() != 18) {
            return false;
        }
        String upper = code.toUpperCase();
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            int value = CHARSET.indexOf(upper.charAt(i));
            if (value < 0) {
                return false; // 字符不在 GB32100 字符集
            }
            sum += value * WEIGHTS[i];
        }
        // GB 32100-2015：校验码 C18 = 31 - (Σ Ci×Wi mod 31)；按标准"结果为 31 时取 0"，即再对 31 取模。
        // ⚠️ 2026-09-21 修正：原实现写成 `CHARSET.charAt(sum % MOD)`（少了「31 −」这一步），
        //    把余数直接当字符序 —— 于是**合法代码全被拒**，只有"照着错误算法反向凑出来"的值能过：
        //      91350100M000100Y43（GB 32100 官方示例）→ 原实现算出 W，实际校验位 3 ⇒ 被拒
        //      914403001922038216（华为）→ 原实现 R，实际 6；91110000802100433B（百度）→ L vs B；
        //      91330100799655058B（阿里）→ L vs B：实测三条公开真实代码全部被拒。
        //    改回标准公式后上述全部通过；库里为迁就旧算法改过的 91610100MAM94DYHJX 会被正确判为非法。
        int checkIndex = (MOD - sum % MOD) % MOD;
        char expect = CHARSET.charAt(checkIndex);
        return upper.charAt(17) == expect;
    }
}
