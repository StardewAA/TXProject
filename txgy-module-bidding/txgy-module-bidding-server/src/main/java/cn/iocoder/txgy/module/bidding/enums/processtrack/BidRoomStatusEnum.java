package cn.iocoder.txgy.module.bidding.enums.processtrack;

/**
 * 开标房间状态（房间生命周期，与开标记录 opening_status 并行维护）。
 * 房间即在线开标室：成员凭 CA 身份认证进房，显示房间成员列表。
 */
public enum BidRoomStatusEnum {

    /** 未开放（房间已建、等待开标时间） */
    WAITING,
    /** 解密中（房间开放，成员可进房，解密投标文件） */
    DECRYPTING,
    /** 唱标中（解密完成，主持唱标） */
    OPENING,
    /** 已结束（房间关闭，成员不可再进） */
    DONE
}
