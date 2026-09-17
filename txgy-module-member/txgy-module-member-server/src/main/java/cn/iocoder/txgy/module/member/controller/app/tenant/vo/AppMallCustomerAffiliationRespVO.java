package cn.iocoder.txgy.module.member.controller.app.tenant.vo;

/** A service relationship, never an enterprise membership or trading permission. */
public record AppMallCustomerAffiliationRespVO(State state, String serviceEnterpriseName) {
    public enum State { DISABLED, NONE, ACTIVE, OWNER_UNAVAILABLE, TEMPORARILY_UNAVAILABLE }

    public static AppMallCustomerAffiliationRespVO of(State state) {
        return new AppMallCustomerAffiliationRespVO(state, null);
    }
}
