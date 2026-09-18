package cn.iocoder.txgy.module.system.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Set;

/**
 * RPC 服务 - 创建管理员用户 Request DTO
 *
 * 目前用于培训模块「批量导入参训人员」时，为名册中存在、但平台尚无账号的学员自动开户
 *
 * @author 云培训学院课题小组
 */
@Schema(description = "RPC 服务 - 创建管理员用户 Request DTO")
@Data
@Accessors(chain = true)
public class AdminUserCreateReqDTO {

    @Schema(description = "用户账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800000001")
    private String username;

    @Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private String nickname;

    @Schema(description = "手机号码", example = "13800000001")
    private String mobile;

    @Schema(description = "部门编号", example = "100")
    private Long deptId;

    @Schema(description = "岗位编号数组", example = "[1,2]")
    private Set<Long> postIds;

    @Schema(description = "密码；为空时使用系统「初始密码」配置(system.user.init-password)", example = "123456")
    private String password;

    @Schema(description = "用户邮箱", example = "txgy@iocoder.cn")
    private String email;

    @Schema(description = "用户性别，参见 SexEnum 枚举类", example = "1")
    private Integer sex;

}
