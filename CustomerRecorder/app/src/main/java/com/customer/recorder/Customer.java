package com.customer.recorder;

/** 一条客户审批记录。 */
public class Customer {
    public long id;
    public String name = "";
    public String product = "";
    /** 额度，单位：元 */
    public double amount;
    /** 利率，如 "5.9800%" */
    public String rate = "";
    /** 首次审批通过日期，格式 yyyy-MM-dd */
    public String approveDate = "";
    public String status = "";
    /** 借记卡：已开通 / 未开通 / 未知 */
    public String debitCard = MessageParser.UNKNOWN;
    /** 手机银行：已开通 / 未开通 / 未知 */
    public String mobileBank = MessageParser.UNKNOWN;
    /** 扫码活动名称 */
    public String activity = "";
    public String gift = "";
    /** 礼品成本，单位：元 */
    public double giftCost;
    public boolean giftGiven;
    public String note = "";
    public String raw = "";
    public long createdAt;
}
