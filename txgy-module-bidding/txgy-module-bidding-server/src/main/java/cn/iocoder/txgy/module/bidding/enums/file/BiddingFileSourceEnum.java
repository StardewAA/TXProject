package cn.iocoder.txgy.module.bidding.enums.file;

/** 文件登记来源（bidding_file.source；用于区分"新增上传"与"历史补登记"，便于审计与治理）。 */
public enum BiddingFileSourceEnum {

    /** 上传（/bidding/file/upload） */
    UPLOAD,
    /** 按访问地址补登记（/bidding/file/register-by-url，仅历史数据修复） */
    BY_URL,
    /** 服务端生成（如评标报告 Word、公告版式 PDF） */
    SERVER
}
