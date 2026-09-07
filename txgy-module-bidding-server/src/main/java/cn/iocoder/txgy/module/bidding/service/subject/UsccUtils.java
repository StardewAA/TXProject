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
        char expect = CHARSET.charAt(sum % MOD);
        return upper.charAt(17) == expect;
    }
}
