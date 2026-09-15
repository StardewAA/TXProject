package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 公开级别，对应规范里"公开范围"的两档：
 * 社会公开 = 公众都能看（公共服务大厅可见）；政务公开 = 只在政务体系内共享（企业后台与监管之间）。
 * 只有规范表里写明了公开范围的字段才标；没写的字段不标，默认不外发。
 */
public enum PublicLevelEnum {

    SOCIAL("社会公开"),
    GOVERNMENT("政务公开");

    private final String label;

    PublicLevelEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
