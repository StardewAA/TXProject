package cn.iocoder.txgy.module.bidding.enums.processtrack;

/**
 * 开标记录状态（开标流程主推进线）。
 */
public enum BidOpeningStatusEnum {

    /** 待开标（开标记录已建，房间未开放） */
    PENDING,
    /** 解密中（投标文件 CA 解密进行中） */
    DECRYPTING,
    /** 已开标（唱标完成，房间结束） */
    DONE
}
