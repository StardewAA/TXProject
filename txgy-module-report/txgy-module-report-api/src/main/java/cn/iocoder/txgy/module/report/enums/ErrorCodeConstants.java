package cn.iocoder.txgy.module.report.enums;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;

/**
 * Report 错误码枚举类
 *
 * report 系统，使用 1-003-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== GoView 模块 1-003-000-000 ==========
    ErrorCode GO_VIEW_PROJECT_NOT_EXISTS = new ErrorCode(1_003_000_000, "GoView 项目不存在");

    // ========== 报表数据集模块 1-003-001-000 ==========
    ErrorCode DATASET_NOT_EXISTS = new ErrorCode(1_003_001_000, "报表数据集不存在");
    ErrorCode DATASET_CODE_DUPLICATE = new ErrorCode(1_003_001_001, "报表数据集编码已存在");
    ErrorCode DATASET_DISABLED = new ErrorCode(1_003_001_002, "报表数据集已停用，禁止查询");
    ErrorCode DATASET_SQL_EMPTY = new ErrorCode(1_003_001_003, "报表数据集 SQL 不能为空");
    ErrorCode DATASET_SQL_NOT_SELECT = new ErrorCode(1_003_001_004, "报表数据集仅允许 SELECT / WITH 查询");
    ErrorCode DATASET_SQL_FORBIDDEN_KEYWORD = new ErrorCode(1_003_001_005, "报表数据集 SQL 含禁止关键字：{}");
    ErrorCode DATASET_SQL_MULTI_STATEMENT = new ErrorCode(1_003_001_006, "报表数据集 SQL 不允许分号或多语句");
    ErrorCode DATASET_PARAM_MISSING = new ErrorCode(1_003_001_007, "报表数据集缺少必填参数：{}");
    ErrorCode DATASET_PARAM_UNDECLARED = new ErrorCode(1_003_001_008, "报表数据集存在未声明的参数：{}");
    ErrorCode DATASET_TENANT_COLUMN_INVALID = new ErrorCode(1_003_001_009, "租户过滤列名不合法：{}");
    ErrorCode DATASET_RESULT_TOO_LARGE = new ErrorCode(1_003_001_010, "查询结果超过数据集行数上限（{} 行），请缩小查询范围");
    ErrorCode DATASET_EXECUTE_FAILED = new ErrorCode(1_003_001_011, "报表数据集查询失败：{}");
    ErrorCode DATASET_RAW_SQL_FORBIDDEN = new ErrorCode(1_003_001_012, "自定义 SQL 查询已受限，请改用数据集编码查询");
    ErrorCode DATASET_PLATFORM_READONLY = new ErrorCode(1_003_001_013, "平台级数据集仅平台管理员可维护");
    ErrorCode DATASET_PARAM_NAME_INVALID = new ErrorCode(1_003_001_014, "参数名不合法：{}");
    ErrorCode DATASET_TENANT_COLUMN_REQUIRED = new ErrorCode(1_003_001_015, "必须声明租户过滤列（tenant_id），报表数据集不允许跨租户取数");
    ErrorCode DATASET_RAW_SQL_TENANT_REQUIRED = new ErrorCode(1_003_001_016, "自定义 SQL 必须显式包含 tenant_id 条件，不允许跨租户查询");

}
