package com.customer.recorder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析银行审批提示信息，例如：
 * 您推荐的客户胡雅萍随e贷申请已审批通过，额度为280000，利率为5.9800%。
 * 该客户首次审批通过的时间为2026年09月24日。该客户未开通我行借记卡！未开通手机银行！
 */
public final class MessageParser {

    public static final String OPENED = "已开通";
    public static final String NOT_OPENED = "未开通";
    public static final String UNKNOWN = "未知";

    /** 已知产品名，用于把“姓名+产品”拆开；未命中时用启发式拆分。 */
    private static final String[] PRODUCTS = {
            "随e贷", "快e贷", "融e借", "融e贷", "e抵快贷", "经营快贷", "网贷通", "信用卡"
    };

    private static final Pattern SPLIT = Pattern.compile("(?=您?推荐的客户)");
    private static final Pattern NAME_PRODUCT =
            Pattern.compile("客户\\s*[:：]?\\s*([^\\s，,。！!；;:：]+?)\\s*的?申请");
    private static final Pattern STATUS = Pattern.compile("申请\\s*([^，,。！!；;\\n]+)");
    private static final Pattern AMOUNT =
            Pattern.compile("额度\\s*(?:为|是)?\\s*[:：]?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(万)?");
    private static final Pattern RATE =
            Pattern.compile("利率\\s*(?:为|是)?\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%");
    private static final Pattern FIRST_DATE = Pattern.compile(
            "首次审批通过的?时间\\s*(?:为|是)?\\s*[:：]?\\s*(\\d{4})\\s*[年\\-/.]\\s*(\\d{1,2})\\s*[月\\-/.]\\s*(\\d{1,2})");
    private static final Pattern ANY_DATE =
            Pattern.compile("(\\d{4})\\s*[年\\-/.]\\s*(\\d{1,2})\\s*[月\\-/.]\\s*(\\d{1,2})");
    private static final Pattern SENTENCE = Pattern.compile("[，,。！!；;\\n]");

    private MessageParser() {
    }

    /** 解析文本，可包含多条提示信息。只返回识别出姓名的记录。 */
    public static List<Customer> parseAll(String text) {
        List<Customer> list = new ArrayList<>();
        if (text == null) return list;
        for (String part : SPLIT.split(text)) {
            Customer c = parse(part);
            if (c != null) list.add(c);
        }
        return list;
    }

    /** 解析单条提示信息，识别不到姓名时返回 null。 */
    public static Customer parse(String text) {
        if (text == null) return null;
        String t = text.trim();
        Matcher m = NAME_PRODUCT.matcher(t);
        if (!m.find()) return null;

        Customer c = new Customer();
        c.raw = t;
        splitNameProduct(m.group(1), c);
        if (c.name.isEmpty()) return null;

        Matcher s = STATUS.matcher(t);
        if (s.find()) c.status = s.group(1).trim();

        Matcher a = AMOUNT.matcher(t);
        if (a.find()) {
            try {
                double v = Double.parseDouble(a.group(1).replace(",", ""));
                if (a.group(2) != null) v *= 10000;
                c.amount = v;
            } catch (NumberFormatException ignored) {
            }
        }

        Matcher r = RATE.matcher(t);
        if (r.find()) c.rate = r.group(1) + "%";

        Matcher d = FIRST_DATE.matcher(t);
        if (!d.find()) {
            d = ANY_DATE.matcher(t);
            if (!d.find()) d = null;
        }
        if (d != null) {
            c.approveDate = String.format("%s-%02d-%02d", d.group(1),
                    Integer.parseInt(d.group(2)), Integer.parseInt(d.group(3)));
        }

        c.debitCard = openState(t, "借记卡");
        c.mobileBank = openState(t, "手机银行");
        return c;
    }

    static void splitNameProduct(String s, Customer c) {
        for (String p : PRODUCTS) {
            int i = s.indexOf(p);
            if (i > 0) {
                c.name = s.substring(0, i);
                c.product = s.substring(i);
                return;
            }
        }
        // 产品名常含字母（如“X e贷”），字母前一个汉字视为产品名开头
        for (int i = 2; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                c.name = s.substring(0, i - 1);
                c.product = s.substring(i - 1);
                return;
            }
        }
        c.name = s;
        c.product = "";
    }

    static String openState(String text, String keyword) {
        for (String sentence : SENTENCE.split(text)) {
            if (!sentence.contains(keyword)) continue;
            if (sentence.contains("未") || sentence.contains("没有") || sentence.contains("尚无")) {
                return NOT_OPENED;
            }
            return OPENED;
        }
        return UNKNOWN;
    }
}
