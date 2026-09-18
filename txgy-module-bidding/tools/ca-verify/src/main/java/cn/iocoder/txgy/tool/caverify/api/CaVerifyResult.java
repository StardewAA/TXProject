package cn.iocoder.txgy.tool.caverify.api;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 校验结果。
 *
 * <p>字段按 01-硬性条件.md R1/R2（结论分级 + 报告要素）预留：当前占位实现只用到
 * success/code/message/filePath/verifiedAt，真实实现可在此基础上扩展签名明细等报告项，
 * 保持本类对外语义不变。</p>
 */
public class CaVerifyResult {

    /** 校验通过 */
    public static final String CODE_SUCCESS = "SUCCESS";
    /** 参数错误（未指定文件等） */
    public static final String CODE_PARAM_ERROR = "PARAM_ERROR";
    /** 文件不存在或不是普通文件 */
    public static final String CODE_FILE_NOT_FOUND = "FILE_NOT_FOUND";
    /** 真实性/完整性校验不通过（真实实现阶段使用） */
    public static final String CODE_VERIFY_FAILED = "VERIFY_FAILED";

    /** 总体结论：true = 校验通过 */
    private final boolean success;
    /** 结果代码：SUCCESS / PARAM_ERROR / FILE_NOT_FOUND / VERIFY_FAILED */
    private final String code;
    /** 人类可读说明 */
    private final String message;
    /** 被校验文件绝对路径（可为 null） */
    private final String filePath;
    /** 校验时间（ISO-8601 本地时间） */
    private final String verifiedAt;

    private CaVerifyResult(boolean success, String code, String message, String filePath) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.filePath = filePath;
        this.verifiedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /** 校验成功结果 */
    public static CaVerifyResult success(String filePath) {
        return new CaVerifyResult(true, CODE_SUCCESS, "真实性校验通过", filePath);
    }

    /** 校验失败/无法判定结果 */
    public static CaVerifyResult failure(String code, String message, String filePath) {
        return new CaVerifyResult(false, code, message, filePath);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getVerifiedAt() {
        return verifiedAt;
    }

    /** 输出为单行 JSON（便于 CLI 与日志），不做格式化 */
    public String toJson() {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        sb.append("\"success\":").append(success);
        sb.append(",\"code\":\"").append(escape(code)).append('"');
        sb.append(",\"message\":\"").append(escape(message)).append('"');
        sb.append(",\"filePath\":\"").append(escape(filePath)).append('"');
        sb.append(",\"verifiedAt\":\"").append(escape(verifiedAt)).append('"');
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                sb.append("\\\"");
            } else if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toJson();
    }

}
