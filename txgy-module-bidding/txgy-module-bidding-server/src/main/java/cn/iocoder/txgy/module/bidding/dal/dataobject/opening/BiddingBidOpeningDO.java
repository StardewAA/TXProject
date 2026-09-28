package cn.iocoder.txgy.module.bidding.dal.dataobject.opening;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 开标记录（在线开标房间的主记录）。表：bidding_bid_opening。
 *
 * <p>房间语义（用户裁定 2026-09-18）：开标视频不做；房间必须做好——显示房间成员
 * （bidding_opening_member）与进房身份认证（外部 CA SDK）；解密投标文件走外部 CA SDK，
 * 本表只记录状态与进度。
 */
@TableName("bidding_bid_opening")
@Data
@EqualsAndHashCode(callSuper = true)
public class BiddingBidOpeningDO extends BaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 招标项目编号 */
    private String tenderProjectCode;
    /** 标段编号（空=覆盖招标项目全部标段） */
    private String bidSectionCode;
    /** 开标时间 */
    private LocalDateTime openingTime;
    /** 开标状态（BidOpeningStatusEnum code：PENDING/DECRYPTING/DONE） */
    private String openingStatus;
    /** 房间状态（BidRoomStatusEnum code：WAITING/DECRYPTING/OPENING/DONE） */
    private String roomStatus;
    /** 房间开放时间 */
    private LocalDateTime roomOpenedTime;
    /** 房间关闭时间 */
    private LocalDateTime roomClosedTime;
    /** 投标人数量 */
    private Integer bidderCount;
    /** 唱标清单（JSON：[{bidderName,bidSectionCode,price,decryptStatus}]） */
    private String openList;
    /** 已解密数 */
    private Integer decryptCount;
    /** 应解密总数 */
    private Integer decryptTotal;
    /** 开标主持人 */
    private String operatorName;
    /** 备注 */
    private String remark;
}
