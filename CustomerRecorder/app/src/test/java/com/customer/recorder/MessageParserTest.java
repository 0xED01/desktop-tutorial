package com.customer.recorder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class MessageParserTest {

    private static final String SAMPLE = "您推荐的客户胡雅萍随e贷申请已审批通过，额度为280000，利率为5.9800%。"
            + "该客户首次审批通过的时间为2026年09月24日。该客户未开通我行借记卡！未开通手机银行！";

    @Test
    public void parsesSample() {
        Customer c = MessageParser.parse(SAMPLE);
        assertEquals("胡雅萍", c.name);
        assertEquals("随e贷", c.product);
        assertEquals("已审批通过", c.status);
        assertEquals(280000, c.amount, 0.001);
        assertEquals("5.9800%", c.rate);
        assertEquals("2026-09-24", c.approveDate);
        assertEquals(MessageParser.NOT_OPENED, c.debitCard);
        assertEquals(MessageParser.NOT_OPENED, c.mobileBank);
    }

    @Test
    public void parsesMultipleAndOpenedStates() {
        String other = "您推荐的客户王五快e贷申请已审批通过，额度为5万，利率为3.65%。"
                + "该客户首次审批通过的时间为2026年10月1日。该客户已开通我行借记卡！未开通手机银行！";
        List<Customer> list = MessageParser.parseAll(SAMPLE + "\n" + other);
        assertEquals(2, list.size());
        Customer c = list.get(1);
        assertEquals("王五", c.name);
        assertEquals("快e贷", c.product);
        assertEquals(50000, c.amount, 0.001);
        assertEquals("2026-10-01", c.approveDate);
        assertEquals(MessageParser.OPENED, c.debitCard);
        assertEquals(MessageParser.NOT_OPENED, c.mobileBank);
    }

    @Test
    public void ignoresUnrelatedText() {
        assertTrue(MessageParser.parseAll("你好").isEmpty());
    }
}
