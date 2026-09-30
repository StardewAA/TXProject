package cn.iocoder.txgy.module.bidding.dal.dataobject.opening;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 开标房间成员（进房身份认证留痕）。表：bidding_opening_member。
 *
 * <p>房间必须显示「房间里有谁」：每名成员一行，记录角色与进房身份认证结果。
 * 身份认证对接外部 CA SDK（IdentityProvider 适配点），auth_result：PENDING→AUTHED/FAILED。
 */
@TableName("bidding_opening_member")
@Data
@EqualsAndHashCode(callSuper = true)
public class BiddingOpeningMemberDO extends BaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 开标记录ID（bidding_bid_opening.id） */
    private Long openingId;
    /** 招标项目编号 */
    private String tenderProjectCode;
    /** 成员用户ID */
    private Long userId;
    /** 成员姓名 */
    private String userName;
    /** 所属企业名称（投标人/招标人） */
    private String subjectName;
    /** 统一社会信用代码 */
    private String subjectCode;
    /** 身份（OpeningMemberRoleEnum code） */
    private String role;
    /** 身份认证方式（CA_CERT/ACCOUNT） */
    private String identityType;
    /** CA 证书序列号 */
    private String caCertNo;
    /** 进房认证结果（PENDING/AUTHED/FAILED） */
    private String authResult;
    /** 认证结果说明（失败原因） */
    private String authMessage;
    /** 认证通过时间 */
    private LocalDateTime authTime;
    /** 进房时间 */
    private LocalDateTime enterTime;
    /** 离房时间 */
    private LocalDateTime leaveTime;
    /** 是否在房间内 */
    private Boolean online;
    /** 备注 */
    private String remark;
}
