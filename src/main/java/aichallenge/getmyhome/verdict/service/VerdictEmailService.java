package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.dto.res.*;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 판정 결과 이메일 발송 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerdictEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from}")
    private String fromAddress;

    public void send(String to, VerdictResponse verdict) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject("[겟마이홈] 청약 판정 결과 (" + verdict.verdictId() + ")");
        helper.setText(buildHtml(verdict), true);

        mailSender.send(message);
        log.info("판정 결과 이메일 발송 완료: verdictId={}, to={}", verdict.verdictId(), to);
    }

    private String buildHtml(VerdictResponse v) {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<style>");
        sb.append("body{font-family:'Apple SD Gothic Neo',sans-serif;max-width:600px;margin:0 auto;padding:20px;color:#333}");
        sb.append("h2{color:#1a56db;border-bottom:2px solid #1a56db;padding-bottom:8px}");
        sb.append("h3{color:#374151;margin-top:24px}");
        sb.append("table{width:100%;border-collapse:collapse;margin:12px 0}");
        sb.append("th,td{padding:10px 12px;text-align:left;border-bottom:1px solid #e5e7eb}");
        sb.append("th{background:#f9fafb;font-weight:600}");
        sb.append(".ok{color:#059669}.block{color:#dc2626}.hold{color:#d97706}.gap{color:#2563eb}");
        sb.append(".meta{color:#6b7280;font-size:13px;margin-bottom:20px}");
        sb.append(".footer{margin-top:32px;padding-top:16px;border-top:1px solid #e5e7eb;color:#9ca3af;font-size:12px}");
        sb.append("</style></head><body>");

        // 헤더
        sb.append("<h2>청약 판정 결과</h2>");
        sb.append("<p class='meta'>판정 ID: ").append(v.verdictId());
        if (v.meta() != null) {
            sb.append(" | 규칙: ").append(v.meta().ruleVersion());
            sb.append(" | 일시: ").append(v.meta().calculatedAt());
        }
        sb.append("</p>");

        // 자금 경로 판정
        if (v.financingRoutes() != null && !v.financingRoutes().isEmpty()) {
            sb.append("<h3>대출 상품별 판정</h3>");
            sb.append("<table><tr><th>상품</th><th>상태</th><th>한도</th></tr>");
            for (FinancingRouteResponse r : v.financingRoutes()) {
                sb.append("<tr><td>").append(r.productCode()).append("</td>");
                sb.append("<td class='").append(statusClass(r.status())).append("'>");
                sb.append(statusLabel(r.status())).append("</td>");
                sb.append("<td>").append(formatLimit(r.limitMin(), r.limitMax())).append("</td></tr>");
            }
            sb.append("</table>");
        }

        // 구간 판정
        if (v.verdicts() != null && !v.verdicts().isEmpty()) {
            sb.append("<h3>구간별 판정</h3>");
            sb.append("<table><tr><th>구간</th><th>상태</th><th>필요</th><th>가용</th><th>부족</th></tr>");
            for (StageVerdictResponse s : v.verdicts()) {
                sb.append("<tr><td>").append(stageLabel(s.stage())).append("</td>");
                sb.append("<td class='").append(statusClass(s.status())).append("'>");
                sb.append(statusLabel(s.status())).append("</td>");
                sb.append("<td>").append(formatManWon(s.required())).append("</td>");
                sb.append("<td>").append(formatManWon(s.available())).append("</td>");
                sb.append("<td>").append(formatManWon(s.gap())).append("</td></tr>");
            }
            sb.append("</table>");
        }

        // 상품별 잔금 비교
        if (v.routeComparisons() != null && !v.routeComparisons().isEmpty()) {
            sb.append("<h3>대출 경로별 잔금 비교</h3>");
            sb.append("<table><tr><th>상품</th><th>상태</th><th>대출 한도</th><th>부족</th><th>시나리오</th></tr>");
            for (RouteBalanceComparison c : v.routeComparisons()) {
                sb.append("<tr><td>").append(c.productName()).append("</td>");
                sb.append("<td class='").append(statusClass(c.status())).append("'>");
                sb.append(statusLabel(c.status())).append("</td>");
                sb.append("<td>").append(formatManWon(c.loanLimit())).append("</td>");
                sb.append("<td>").append(formatManWon(c.gap())).append("</td>");
                sb.append("<td>").append(c.scenario() != null ? c.scenario() : "-").append("</td></tr>");
            }
            sb.append("</table>");
        }

        sb.append("<div class='footer'>");
        sb.append("본 결과는 입력 조건 기반 추정치이며, 실제 대출 심사 결과와 다를 수 있습니다.<br>");
        sb.append("겟마이홈 - 청약 판정 서비스");
        sb.append("</div></body></html>");

        return sb.toString();
    }

    private String statusClass(VerdictStatus status) {
        if (status == null) return "";
        return switch (status) {
            case OK -> "ok";
            case BLOCK -> "block";
            case HOLD -> "hold";
            case GAP -> "gap";
        };
    }

    private String statusLabel(VerdictStatus status) {
        if (status == null) return "-";
        return switch (status) {
            case OK -> "가능";
            case BLOCK -> "불가";
            case HOLD -> "확인 필요";
            case GAP -> "부족";
        };
    }

    private String stageLabel(String stage) {
        if (stage == null) return "-";
        return switch (stage) {
            case "CONTRACT" -> "계약금";
            case "INTERIM" -> "중도금";
            case "BALANCE" -> "잔금";
            default -> stage;
        };
    }

    private String formatLimit(Integer min, Integer max) {
        if (max == null) return "-";
        if (min != null && !min.equals(max)) {
            return formatManWon(min) + " ~ " + formatManWon(max);
        }
        return formatManWon(max);
    }

    private String formatManWon(Integer value) {
        if (value == null) return "-";
        if (value >= 10000) {
            int uk = value / 10000;
            int remainder = value % 10000;
            return remainder == 0 ? uk + "억" : uk + "억 " + String.format("%,d", remainder) + "만";
        }
        return String.format("%,d", value) + "만 원";
    }
}