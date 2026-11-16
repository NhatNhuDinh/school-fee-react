package com.dth.frw.module.school.fee.notification.service;

import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.FeeLine;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.MailContext;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Builds the e-mail a parent receives, as one HTML document.
 *
 * <p>The layout is the "phiếu" designed for the notification: a white card on a grey page,
 * the school and the payment code across the top, the title, the amount and the deadline
 * where the eye lands first, then the student, the khoản thu, and how to pay. It is tables
 * and inline styles because mail clients ignore everything else.
 *
 * <p>Two weights (regular and semi-bold), two text colours (ink and grey) and three sizes
 * (12, 14, 20) — the same discipline the screen holds itself to. The school logo is
 * referenced as {@code cid:school-logo} for the real mail, which attaches the image inline,
 * and as a data URI for the preview in the browser.
 */
@Component
public class FeeNotificationEmailRenderer {

    public static final String LOGO_CONTENT_ID = "school-logo";
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String FONT = "Inter,-apple-system,'Segoe UI',Arial,sans-serif";
    private static final String INK = "#1a1a1f";
    private static final String GREY = "#73737a";
    private static final String NAVY = "#12305c";
    private static final String LINE = "#b8cae6";
    private static final String BORDER = "#9eb8dc";
    private static final String STRONG_LINE = "#12305c";

    /** A school logo decoded from the data URL stored on the organization. */
    public record Logo(byte[] bytes, String mime) {
        public static Logo parse(String dataUrl) {
            if (dataUrl == null || !dataUrl.startsWith("data:") || !dataUrl.contains(";base64,")) {
                return null;
            }
            int comma = dataUrl.indexOf(',');
            String mime = dataUrl.substring(5, dataUrl.indexOf(';'));
            try {
                return new Logo(Base64.getDecoder().decode(dataUrl.substring(comma + 1)), mime);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** The VietQR image for a payment: the account, the amount and the order code that goes in the transfer. */
    public static String qrUrl(MailContext c) {
        String amount = c.total() == null ? "0" : c.total().stripTrailingZeros().toPlainString();
        return nullToEmpty(c.vietQrBaseUrl())
                + "?acc=" + encode(c.bankAccountNumber())
                + "&bank=" + encode(c.bankCode())
                + "&amount=" + encode(amount)
                + "&des=" + encode(c.orderCode())
                + "&template=" + encode(c.vietQrTemplate())
                + "&showinfo=" + encode(c.vietQrShowInfo())
                + "&holder=" + encode(c.bankAccountHolder());
    }

    public String render(MailContext c, String logoSrc) {
        String money = money(c.total());
        StringBuilder rows = new StringBuilder();
        int index = 0;
        for (FeeLine line : c.lines()) {
            index++;
            rows.append("<tr>")
                    .append(cell("padding:10px 0;font-size:14px;color:" + INK, esc(line.name())))
                    .append(cell("padding:10px 0;font-size:14px;color:" + INK + ";text-align:right;white-space:nowrap",
                            money(line.amount())))
                    .append("</tr>");
        }

        return "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + esc(c.subject()) + "</title></head>"
                + "<body style=\"margin:0;padding:24px 12px;background:#f4f4f5;font-family:" + FONT + ";color:" + INK + "\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"560\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" "
                + "style=\"width:100%;max-width:560px;background:#ffffff;border:1px solid " + BORDER + ";border-radius:8px;border-collapse:separate;overflow:hidden\">"
                // accent bar
                + "<tr><td style=\"height:5px;line-height:5px;font-size:0;background:" + NAVY + "\">&nbsp;</td></tr>"
                + "<tr><td style=\"padding:32px 40px 0\">"
                // header: school left, payment code right
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"><tr>"
                + (logoSrc == null || logoSrc.isBlank() ? "" : "<td width=\"48\" valign=\"middle\"><img src=\"" + attr(logoSrc) + "\" width=\"36\" height=\"36\" alt=\"\" "
                + "style=\"display:block;width:36px;height:36px;border-radius:8px\"></td>")
                + "<td valign=\"middle\" style=\"padding-left:4px\">"
                + "<div style=\"font-size:14px;font-weight:600;line-height:20px;color:" + INK + "\">" + esc(c.schoolName()) + "</div>"
                + "<div style=\"font-size:12px;line-height:18px;color:" + GREY + "\">" + esc(c.senderUnit()) + "</div></td>"
                + "<td valign=\"middle\" align=\"right\">"
                + "<div style=\"font-size:12px;line-height:18px;color:" + GREY + "\">Mã phiếu</div>"
                + "<div style=\"font-size:14px;font-weight:600;line-height:20px;color:" + INK + "\">" + esc(c.orderCode()) + "</div></td>"
                + "</tr></table>"
                + "<div style=\"margin-top:24px;border-top:1px solid " + LINE + "\"></div>"
                // title + status
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"margin-top:24px\"><tr>"
                + "<td valign=\"middle\" style=\"font-size:20px;font-weight:600;line-height:28px;color:" + INK + "\">" + esc(c.subject()) + "</td>"
                + "<td valign=\"middle\" align=\"right\" width=\"140\">"
                + "<span style=\"display:inline-block;padding:3px 10px;border-radius:13px;background:#fff5e0;font-size:12px;line-height:18px;color:#8c610a;white-space:nowrap\">"
                + "<span style=\"color:#d98c0d\">&#9679;</span>&nbsp;Chưa thanh toán</span></td>"
                + "</tr></table>"
                // greeting + body
                + "<div style=\"margin-top:20px;font-size:14px;line-height:22px;color:" + INK + "\">Kính gửi Quý phụ huynh học sinh "
                + esc(c.studentName()) + ",</div>"
                + "<div style=\"margin-top:8px;font-size:14px;line-height:22px;color:" + INK + "\">" + bodyLines(c.content()) + "</div>"
                // amount + deadline
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"margin-top:28px\"><tr>"
                + "<td width=\"50%\" valign=\"top\"><div style=\"font-size:12px;line-height:18px;color:" + GREY + "\">Số tiền cần nộp</div>"
                + "<div style=\"margin-top:4px;font-size:20px;font-weight:600;line-height:28px;color:" + INK + "\">" + money + " VND</div></td>"
                + "<td width=\"50%\" valign=\"top\"><div style=\"font-size:12px;line-height:18px;color:" + GREY + "\">Hạn đóng</div>"
                + "<div style=\"margin-top:4px;font-size:20px;font-weight:600;line-height:28px;color:" + INK + "\">" + c.deadline().format(DATE) + "</div></td>"
                + "</tr></table>"
                + "<div style=\"margin-top:24px;border-top:2px dashed #8ca8d6\"></div>"
                // student
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"margin-top:16px\">"
                + infoRow("Họ và tên", c.studentName())
                + infoRow("Lớp", c.className())
                + infoRow("Mã học sinh", c.studentCode())
                + "</table>"
                + "<div style=\"margin-top:16px;border-top:1px solid " + LINE + "\"></div>"
                // fee lines
                + "<div style=\"margin-top:24px;font-size:14px;font-weight:600;line-height:20px;color:" + INK + "\">Chi tiết khoản thu</div>"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"margin-top:12px;border-collapse:collapse\">"
                + "<tr>" + cell("padding:0 0 8px;font-size:12px;color:" + GREY + ";border-bottom:1px solid " + STRONG_LINE, "Khoản thu")
                + cell("padding:0 0 8px;font-size:12px;color:" + GREY + ";text-align:right;border-bottom:1px solid " + STRONG_LINE, "Số tiền (VND)") + "</tr>"
                + rows
                + "<tr>" + cell("padding:12px 0 0;font-size:14px;font-weight:600;color:" + INK + ";border-top:1px solid " + STRONG_LINE, "Tổng cộng")
                + cell("padding:12px 0 0;font-size:14px;font-weight:600;color:" + INK + ";text-align:right;border-top:1px solid " + STRONG_LINE, money) + "</tr>"
                + "</table>"
                // payment
                + "<div style=\"margin-top:32px;font-size:14px;font-weight:600;line-height:20px;color:" + INK + "\">Thanh toán bằng chuyển khoản</div>"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" "
                + "style=\"margin-top:12px;margin-bottom:32px;border-collapse:separate\"><tr>"
                + "<td width=\"156\" valign=\"top\" align=\"center\" style=\"padding:0 16px 0 0\">"
                + "<img src=\"" + attr(qrUrl(c)) + "\" width=\"132\" height=\"132\" alt=\"Mã QR thanh toán\" "
                + "style=\"display:block;width:132px;height:132px;background:#ffffff\">"
                + "<div style=\"margin-top:8px;font-size:12px;line-height:18px;color:" + GREY + "\">Quét mã QR để thanh toán</div></td>"
                + "<td valign=\"top\" style=\"padding:0\"><table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">"
                + payRow("Ngân hàng", c.bankCode())
                + payRow("Số tài khoản", c.bankAccountNumber())
                + payRow("Chủ tài khoản", c.bankAccountHolder())
                + payRow("Số tiền", money + " VND")
                + payRow("Nội dung", c.orderCode())
                + "</table></td></tr></table>"
                + "</td></tr></table>"
                + "<div style=\"max-width:560px;margin-top:16px;font-size:12px;line-height:18px;color:" + GREY + ";text-align:center\">"
                + "Email tự động từ hệ thống thu phí của nhà trường. Vui lòng không trả lời email này.</div>"
                + "</td></tr></table></body></html>";
    }

    // ---- pieces

    private static String infoRow(String label, String value) {
        return "<tr>" + cell("padding:4px 0;font-size:14px;line-height:22px;color:" + GREY + ";width:120px", label)
                + cell("padding:4px 0;font-size:14px;line-height:22px;color:" + INK, esc(value)) + "</tr>";
    }

    private static String payRow(String label, String value) {
        return "<tr>" + cell("padding:3px 0;font-size:14px;line-height:22px;color:" + GREY + ";width:100px;vertical-align:top", label)
                + cell("padding:3px 0;font-size:14px;line-height:22px;color:" + INK + ";word-break:break-all", esc(value)) + "</tr>";
    }

    private static String cell(String style, String html) {
        return "<td style=\"" + style + "\">" + html + "</td>";
    }

    private static String bodyLines(String content) {
        StringBuilder out = new StringBuilder();
        for (String line : nullToEmpty(content).split("\\R", -1)) {
            out.append("<div>").append(line.isBlank() ? "&nbsp;" : esc(line)).append("</div>");
        }
        return out.toString();
    }

    /** {@code 155.000}: Vietnamese grouping, no decimals. */
    public static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(VIETNAMESE);
        format.setMaximumFractionDigits(0);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    private static String esc(String value) {
        return nullToEmpty(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String attr(String value) {
        return esc(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(nullToEmpty(value), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
