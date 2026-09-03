package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.dto.res.*;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * PDF 미리보기용 테스트 — docs/sample_verdict.pdf 로 출력
 */
class PdfPreviewTest {

    @Test
    void generateSamplePdf() throws Exception {
        VerdictResponse verdict = new VerdictResponse(
            "V-sample01",
            new VerdictResponse.VerdictMeta("v2026-08", "default", "2026-09-01", "step2", null,
                "래미안 원베일리", "2025000001", "084.9900A", 120000, 5000, 100, 42),
            VerdictStatus.GAP, "HOLD", null, null,
            List.of(
                new FinancingRouteResponse("DIDIMDOL_GENERAL", "디딤돌 대출 - 일반", VerdictStatus.OK, null, 20000, "DTI", null, List.of()),
                new FinancingRouteResponse("DIDIMDOL_FIRST", "디딤돌 대출 - 생애최초", VerdictStatus.HOLD, null, null, null, "NEED_FIRST_TIME_INFO", List.of()),
                new FinancingRouteResponse("DIDIMDOL_NEWLYWED", "디딤돌 대출 - 신혼부부", VerdictStatus.HOLD, null, null, null, "NEED_SPOUSE_INCOME", List.of()),
                new FinancingRouteResponse("BANK_MORTGAGE", "시중은행 주택담보대출", VerdictStatus.OK, 31046, 34910, "DSR", null, List.of())
            ),
            List.of(),
            List.of(
                new StageVerdictResponse("CONTRACT", VerdictStatus.OK, 4200, 5000, null, null, null, List.of(), List.of(), "5,000만 원으로 계약금 4,200만 원 충당 가능", "2027-03-15"),
                new StageVerdictResponse("INTERIM", VerdictStatus.HOLD, null, null, null, null, null, List.of(), List.of(), "AI 분석 미검수, 중도금 비율 확인 보류", null),
                new StageVerdictResponse("BALANCE", VerdictStatus.GAP, 37800, 31800, 6000, 33, 53, List.of("월 95만 원씩 모으면 32개월 만에 채울 수 있어요."), List.of(), "잔금 6,000만 원 부족, 월 95만 원 저축 시 53개월 소요", "2029-06-30")
            ),
            List.of(
                new RouteBalanceComparison("DIDIMDOL_GENERAL", "디딤돌 대출 - 일반", VerdictStatus.GAP, 20000, 37800, 25000, 12800, 17800, 33, 53, "월 95만 원 저축 시 53개월 필요 (잔금일까지 33개월)"),
                new RouteBalanceComparison("BANK_MORTGAGE", "시중은행 주택담보대출", VerdictStatus.OK, 34910, 37800, 39910, null, null, 33, null, null)
            ),
            null, null, null,
            List.of(
                new HoldResponse("NEED_FIRST_TIME_INFO", "생애최초 여부, 세대 구성, 순자산 정보를 입력해 주세요.", "생애최초 여부, 세대 구성, 순자산 정보를 입력해 주세요.", null, null, null),
                new HoldResponse("NEED_SPOUSE_INCOME", "배우자 연소득을 입력해 주세요.", "배우자 연소득을 입력해 주세요.", null, null, null)
            ),
            List.of(),
            null,
            List.of(),
            null
        );

        // PDF HTML 생성 (VerdictEmailService의 buildPdfHtml과 동일한 로직)
        String html = buildPdfHtml(verdict);

        // PDF 파일 생성 (클래스패스 한글 폰트 등록)
        byte[] pdfBytes;
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();

            try (var fontStream = getClass().getResourceAsStream("/fonts/NanumSquareNeo-Regular.ttf")) {
                if (fontStream != null) {
                    java.io.File tempFont = java.io.File.createTempFile("korean-font", ".ttf");
                    tempFont.deleteOnExit();
                    java.nio.file.Files.copy(fontStream, tempFont.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    builder.useFont(tempFont, "KoreanFont");
                }
            }

            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            pdfBytes = os.toByteArray();
        }

        Path docsDir = Path.of(System.getProperty("user.dir"), "docs");
        java.nio.file.Files.createDirectories(docsDir);
        Path pdfOutput = docsDir.resolve("sample_verdict.pdf");
        try (FileOutputStream fos = new FileOutputStream(pdfOutput.toFile())) {
            fos.write(pdfBytes);
        }
        System.out.println("PDF 생성 완료: " + pdfOutput.toAbsolutePath());

        // 이메일 HTML 미리보기도 생성
        String emailHtml = buildEmailHtml(verdict);
        Path htmlOutput = docsDir.resolve("sample_email.html");
        java.nio.file.Files.writeString(htmlOutput, emailHtml);
        System.out.println("이메일 HTML 생성 완료: " + htmlOutput.toAbsolutePath());
    }

    // ── VerdictEmailService.buildPdfHtml 복제 (테스트 독립 실행용) ──

    private String buildPdfHtml(VerdictResponse v) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">");
        sb.append("<head><meta charset=\"UTF-8\" />");
        sb.append("<style>");
        sb.append("body{font-family:'KoreanFont','Malgun Gothic','Apple SD Gothic Neo',sans-serif;margin:0;padding:30px;color:#333;font-size:12px;}");
        sb.append("h1{font-size:22px;color:#6366f1;margin:0 0 4px;}");
        sb.append(".subtitle{color:#6b7280;font-size:11px;margin-bottom:24px;}");
        sb.append("h2{font-size:14px;color:#1f2937;border-bottom:2px solid #e5e7eb;padding-bottom:6px;margin:24px 0 12px;}");
        sb.append(".card{border:1px solid #e5e7eb;border-radius:8px;padding:12px 14px;margin-bottom:10px;page-break-inside:avoid;}");
        sb.append(".card-header{margin-bottom:6px;}");
        sb.append(".product-name{font-size:13px;font-weight:bold;color:#374151;}");
        sb.append(".badge{display:inline-block;padding:2px 10px;border-radius:12px;font-size:11px;font-weight:bold;float:right;}");
        sb.append(".badge-ok{background:#d1fae5;color:#065f46}");
        sb.append(".badge-block{background:#fee2e2;color:#991b1b}");
        sb.append(".badge-hold{background:#fef3c7;color:#92400e}");
        sb.append(".badge-gap{background:#dbeafe;color:#1e40af}");
        sb.append(".detail{color:#6b7280;font-size:11px;margin-top:4px;}");
        sb.append(".detail strong{color:#1f2937;}");
        sb.append(".scenario-box{background:#f0fdf4;border-radius:6px;padding:8px 10px;margin-top:8px;font-size:11px;color:#166534;}");
        sb.append(".hold-box{background:#fffbeb;border-radius:6px;padding:8px 10px;margin-top:8px;font-size:11px;color:#92400e;}");
        sb.append(".gap-text{color:#dc2626;}");
        sb.append(".footer{margin-top:30px;padding-top:12px;border-top:1px solid #e5e7eb;font-size:10px;color:#9ca3af;}");
        sb.append(".clearfix:after{content:'';display:table;clear:both;}");
        sb.append("</style></head><body>");

        sb.append("<h1>Homm, 살 수 있어?</h1>");
        sb.append("<p class=\"subtitle\">청약 판정 결과 리포트");
        if (v.meta() != null && v.meta().calculatedAt() != null) {
            sb.append(" · ").append(v.meta().calculatedAt()).append(" 기준");
        }
        sb.append("</p>");

        if (v.financingRoutes() != null && !v.financingRoutes().isEmpty()) {
            sb.append("<h2>대출 상품별 판정</h2>");
            for (FinancingRouteResponse r : v.financingRoutes()) {
                String name = r.productName() != null ? r.productName() : r.productCode();
                sb.append("<div class=\"card\"><div class=\"card-header clearfix\">");
                sb.append("<span class=\"product-name\">").append(name).append("</span>");
                sb.append("<span class=\"badge badge-").append(statusClass(r.status())).append("\">").append(statusLabel(r.status())).append("</span>");
                sb.append("</div>");
                if (r.status() == VerdictStatus.OK && r.limitMax() != null) {
                    sb.append("<div class=\"detail\">예상 한도: <strong>").append(formatLimit(r.limitMin(), r.limitMax())).append("</strong>");
                    if (r.bindingFactor() != null) {
                        sb.append(" (").append(r.bindingFactor()).append(" 기준)");
                    }
                    sb.append("</div>");
                }
                if (r.status() == VerdictStatus.HOLD && r.reasonCode() != null) {
                    sb.append("<div class=\"hold-box\">").append(holdMessage(r.reasonCode())).append("</div>");
                }
                sb.append("</div>");
            }
        }

        if (v.verdicts() != null && !v.verdicts().isEmpty()) {
            sb.append("<h2>계약금 · 중도금 · 잔금 단계별 결과</h2>");
            for (StageVerdictResponse s : v.verdicts()) {
                sb.append("<div class=\"card\"><div class=\"card-header clearfix\">");
                sb.append("<span class=\"product-name\">").append(stageLabel(s.stage()));
                if (s.required() != null) {
                    sb.append("&nbsp;&nbsp;").append(formatManWon(s.required()));
                }
                sb.append("</span>");
                sb.append("<span class=\"badge badge-").append(statusClass(s.status())).append("\">").append(statusLabel(s.status())).append("</span>");
                sb.append("</div>");
                if (s.available() != null) {
                    sb.append("<div class=\"detail\">가용 금액: ").append(formatManWon(s.available())).append("</div>");
                }
                if (s.gap() != null) {
                    sb.append("<div class=\"detail gap-text\">부족 금액: ").append(formatManWon(s.gap())).append("</div>");
                }
                if (s.scenarios() != null && !s.scenarios().isEmpty()) {
                    sb.append("<div class=\"scenario-box\">");
                    for (String scenario : s.scenarios()) {
                        sb.append(scenario).append("<br />");
                    }
                    sb.append("</div>");
                }
                sb.append("</div>");
            }
        }

        if (v.routeComparisons() != null && !v.routeComparisons().isEmpty()) {
            sb.append("<h2>대출 상품별 잔금 비교</h2>");
            for (RouteBalanceComparison c : v.routeComparisons()) {
                sb.append("<div class=\"card\"><div class=\"card-header clearfix\">");
                sb.append("<span class=\"product-name\">").append(c.productName()).append("</span>");
                sb.append("<span class=\"badge badge-").append(statusClass(c.status())).append("\">").append(statusLabel(c.status())).append("</span>");
                sb.append("</div>");
                sb.append("<div class=\"detail\">대출 한도: ").append(formatManWon(c.loanLimit()));
                if (c.gap() != null) {
                    sb.append(" · 부족: <span class=\"gap-text\">").append(formatManWon(c.gap())).append("</span>");
                }
                sb.append("</div>");
                if (c.scenario() != null) {
                    sb.append("<div class=\"scenario-box\">").append(c.scenario()).append("</div>");
                }
                sb.append("</div>");
            }
        }

        if (v.holds() != null && !v.holds().isEmpty()) {
            sb.append("<h2>확인 필요 항목</h2>");
            for (HoldResponse h : v.holds()) {
                sb.append("<div class=\"hold-box\" style=\"margin-bottom:8px;\">").append(h.nextAction()).append("</div>");
            }
        }

        sb.append("<div class=\"footer\">");
        sb.append("이 결과는 공개 자료 기준 추정이며, 최종 확정은 금융기관 심사에 따릅니다.<br />");
        sb.append("Homm, 살 수 있어? - 청약 판정 서비스");
        sb.append("</div>");

        sb.append("</body></html>");
        return sb.toString();
    }

    private String statusClass(VerdictStatus s) {
        return s == null ? "" : switch (s) { case OK -> "ok"; case BLOCK -> "block"; case HOLD -> "hold"; case GAP -> "gap"; };
    }
    private String statusLabel(VerdictStatus s) {
        return s == null ? "-" : switch (s) { case OK -> "가능"; case BLOCK -> "불가"; case HOLD -> "확인필요"; case GAP -> "부족"; };
    }
    private String stageLabel(String s) {
        return s == null ? "-" : switch (s) { case "CONTRACT" -> "계약금"; case "INTERIM" -> "중도금"; case "BALANCE" -> "잔금"; default -> s; };
    }
    private String holdMessage(String code) {
        if (code == null) return "";
        return switch (code) {
            case "NEED_SPOUSE_INCOME" -> "배우자 연소득을 입력하면 더 정확한 판정이 가능합니다.";
            case "NEED_HOUSEHOLD_INFO" -> "세대 구성, 세대원 무주택 여부, 순자산 정보를 입력해 주세요.";
            case "NEED_FIRST_TIME_INFO" -> "생애최초 여부, 세대 구성, 순자산 정보를 입력해 주세요.";
            default -> code;
        };
    }
    private String formatLimit(Integer min, Integer max) {
        if (max == null) return "-";
        if (min != null && !min.equals(max)) return formatManWon(min) + " ~ " + formatManWon(max);
        return formatManWon(max);
    }
    private String formatManWon(Integer v) {
        if (v == null) return "-";
        if (v >= 10000) { int u=v/10000; int r=v%10000; return r==0 ? u+"억원" : u+"억 "+String.format("%,d",r)+"만원"; }
        return String.format("%,d",v)+"만원";
    }
    private String statusBgColor(VerdictStatus s) {
        if (s == null) return "#f3f4f6";
        return switch (s) { case OK -> "#d1fae5"; case BLOCK -> "#fee2e2"; case HOLD -> "#fef3c7"; case GAP -> "#dbeafe"; };
    }
    private String statusTextColor(VerdictStatus s) {
        if (s == null) return "#374151";
        return switch (s) { case OK -> "#065f46"; case BLOCK -> "#991b1b"; case HOLD -> "#92400e"; case GAP -> "#1e40af"; };
    }
    private String bindingLabel(String f) { return f == null ? "" : f; }

    private String buildEmailHtml(VerdictResponse v) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head>");
        sb.append("<body style=\"margin:0;padding:0;background:#f5f5f5;font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;\">");
        sb.append("<div style=\"max-width:600px;margin:0 auto;background:#ffffff;\">");
        sb.append("<div style=\"background:linear-gradient(135deg,#6366f1,#8b5cf6);padding:24px 28px;\">");
        sb.append("<h1 style=\"margin:0;color:#fff;font-size:20px;\">Homm, 살 수 있어?</h1>");
        sb.append("<p style=\"margin:6px 0 0;color:rgba(255,255,255,0.85);font-size:13px;\">청약 판정 결과 리포트</p>");
        sb.append("</div><div style=\"padding:24px 28px;\">");

        if (v.financingRoutes() != null && !v.financingRoutes().isEmpty()) {
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:0 0 16px;padding-bottom:8px;border-bottom:2px solid #e5e7eb;\">대출 상품별 판정</h2>");
            for (FinancingRouteResponse r : v.financingRoutes()) {
                String name = r.productName() != null ? r.productName() : r.productCode();
                sb.append("<div style=\"border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-bottom:12px;\">");
                sb.append("<div style=\"display:flex;justify-content:space-between;align-items:center;\">");
                sb.append("<span style=\"font-size:15px;font-weight:600;color:#374151;\">").append(name).append("</span>");
                sb.append("<span style=\"background:").append(statusBgColor(r.status())).append(";color:").append(statusTextColor(r.status()));
                sb.append(";padding:4px 12px;border-radius:20px;font-size:13px;font-weight:600;\">").append(statusLabel(r.status())).append("</span>");
                sb.append("</div>");
                if (r.status() == VerdictStatus.OK && r.limitMax() != null) {
                    sb.append("<div style=\"margin-top:10px;color:#6b7280;font-size:13px;\">예상 한도: <strong style=\"color:#1f2937;\">").append(formatLimit(r.limitMin(), r.limitMax())).append("</strong>");
                    if (r.bindingFactor() != null) sb.append(" <span style=\"color:#9ca3af;\">(").append(bindingLabel(r.bindingFactor())).append(" 기준)</span>");
                    sb.append("</div>");
                }
                if (r.status() == VerdictStatus.HOLD && r.reasonCode() != null) {
                    sb.append("<div style=\"margin-top:10px;background:#fffbeb;border-radius:8px;padding:10px 12px;font-size:13px;color:#92400e;\">").append(holdMessage(r.reasonCode())).append("</div>");
                }
                sb.append("</div>");
            }
        }
        if (v.verdicts() != null && !v.verdicts().isEmpty()) {
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:28px 0 16px;padding-bottom:8px;border-bottom:2px solid #e5e7eb;\">계약금 · 중도금 · 잔금 단계별 결과</h2>");
            for (StageVerdictResponse s : v.verdicts()) {
                sb.append("<div style=\"border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-bottom:12px;\">");
                sb.append("<div style=\"display:flex;justify-content:space-between;align-items:center;\"><div>");
                sb.append("<span style=\"font-size:15px;font-weight:600;color:#374151;\">").append(stageLabel(s.stage())).append("</span>");
                if (s.required() != null) sb.append("&nbsp;&nbsp;<span style=\"font-size:14px;color:#6366f1;font-weight:700;\">").append(formatManWon(s.required())).append("</span>");
                sb.append("</div>");
                sb.append("<span style=\"background:").append(statusBgColor(s.status())).append(";color:").append(statusTextColor(s.status()));
                sb.append(";padding:4px 12px;border-radius:20px;font-size:13px;font-weight:600;\">").append(statusLabel(s.status())).append("</span></div>");
                if (s.available() != null) sb.append("<div style=\"margin-top:8px;font-size:13px;color:#6b7280;\">가용 금액: ").append(formatManWon(s.available())).append("</div>");
                if (s.gap() != null) sb.append("<div style=\"margin-top:4px;font-size:13px;color:#dc2626;\">부족 금액: ").append(formatManWon(s.gap())).append("</div>");
                if (s.scenarios() != null && !s.scenarios().isEmpty()) {
                    sb.append("<div style=\"margin-top:10px;background:#f0fdf4;border-radius:8px;padding:10px 12px;font-size:13px;color:#166534;\">");
                    for (String sc : s.scenarios()) sb.append(sc).append("<br>");
                    sb.append("</div>");
                }
                sb.append("</div>");
            }
        }
        if (v.routeComparisons() != null && !v.routeComparisons().isEmpty()) {
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:28px 0 16px;padding-bottom:8px;border-bottom:2px solid #e5e7eb;\">대출 상품별 잔금 비교</h2>");
            for (RouteBalanceComparison c : v.routeComparisons()) {
                sb.append("<div style=\"border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-bottom:12px;\">");
                sb.append("<div style=\"display:flex;justify-content:space-between;align-items:center;\">");
                sb.append("<span style=\"font-size:14px;font-weight:600;color:#374151;\">").append(c.productName()).append("</span>");
                sb.append("<span style=\"background:").append(statusBgColor(c.status())).append(";color:").append(statusTextColor(c.status()));
                sb.append(";padding:4px 12px;border-radius:20px;font-size:13px;font-weight:600;\">").append(statusLabel(c.status())).append("</span></div>");
                sb.append("<div style=\"margin-top:8px;font-size:13px;color:#6b7280;\">대출 한도: ").append(formatManWon(c.loanLimit()));
                if (c.gap() != null) sb.append(" · 부족: <span style=\"color:#dc2626;\">").append(formatManWon(c.gap())).append("</span>");
                sb.append("</div>");
                if (c.scenario() != null) sb.append("<div style=\"margin-top:10px;background:#f0fdf4;border-radius:8px;padding:10px 12px;font-size:13px;color:#166534;\">").append(c.scenario()).append("</div>");
                sb.append("</div>");
            }
        }
        if (v.holds() != null && !v.holds().isEmpty()) {
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:28px 0 16px;padding-bottom:8px;border-bottom:2px solid #e5e7eb;\">확인 필요 항목</h2>");
            for (HoldResponse h : v.holds()) {
                sb.append("<div style=\"background:#fffbeb;border:1px solid #fde68a;border-radius:12px;padding:14px 16px;margin-bottom:10px;font-size:13px;color:#92400e;\">").append(h.nextAction()).append("</div>");
            }
        }
        sb.append("</div>");
        sb.append("<div style=\"background:#f9fafb;padding:20px 28px;border-top:1px solid #e5e7eb;\">");
        sb.append("<p style=\"margin:0;font-size:11px;color:#9ca3af;line-height:1.6;\">이 결과는 공개 자료 기준 추정이며, 최종 확정은 금융기관 심사에 따릅니다.");
        if (v.meta() != null && v.meta().calculatedAt() != null) sb.append("<br>").append(v.meta().calculatedAt()).append(" 규정 기준");
        sb.append("<br>Homm, 살 수 있어? - 청약 판정 서비스</p></div>");
        sb.append("</div></body></html>");
        return sb.toString();
    }
}