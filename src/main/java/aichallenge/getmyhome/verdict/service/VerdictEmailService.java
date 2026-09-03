package aichallenge.getmyhome.verdict.service;

import aichallenge.getmyhome.verdict.client.dto.FundingStressResponse;
import aichallenge.getmyhome.verdict.dto.res.*;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        helper.setSubject("[Homm] 청약 자금 완주 진단서");
        helper.setText(buildEmailHtml(verdict), true);

        // PDF 첨부
        try {
            byte[] pdfBytes = generatePdf(buildPdfHtml(verdict));
            DataSource pdfDataSource = new ByteArrayDataSource(pdfBytes, "application/pdf");
            helper.addAttachment("Homm_청약자금완주진단서.pdf", pdfDataSource);
        } catch (Exception e) {
            log.warn("PDF 생성 실패, 이메일 본문만 발송합니다: {}", e.getMessage());
        }

        mailSender.send(message);
        log.info("판정 결과 이메일 발송 완료: verdictId={}, to={}", verdict.verdictId(), to);
    }

    // ── PDF 생성 ──

    private static final String FONT_FAMILY = "SystemGothic";
    private static final String BUNDLED_FONT = "/fonts/NanumSquareNeo-Regular.ttf";
    private static final String[] SYSTEM_FONT_PATHS = {
        "/System/Library/Fonts/AppleSDGothicNeo.ttc",
        "/System/Library/Fonts/Supplemental/AppleSDGothicNeo-Regular.otf",
        "C:\\Windows\\Fonts\\malgun.ttf",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
    };

    private byte[] generatePdf(String html) throws Exception {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();

            // 시스템 기본 고딕 폰트 탐색 → 없으면 번들 폰트 fallback
            File systemFont = findSystemFont();
            if (systemFont != null) {
                builder.useFont(systemFont, FONT_FAMILY);
                log.info("시스템 폰트 사용: {}", systemFont.getAbsolutePath());
            } else {
                try (var fontStream = getClass().getResourceAsStream(BUNDLED_FONT)) {
                    if (fontStream != null) {
                        File tempFont = File.createTempFile("korean-font", ".ttf");
                        tempFont.deleteOnExit();
                        Files.copy(fontStream, tempFont.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        builder.useFont(tempFont, FONT_FAMILY);
                    } else {
                        log.warn("한글 폰트를 찾을 수 없습니다");
                    }
                }
            }

            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }

    private File findSystemFont() {
        for (String path : SYSTEM_FONT_PATHS) {
            File f = new File(path);
            if (f.exists()) return f;
        }
        return null;
    }

    // ── 이메일 HTML (인라인 스타일, 이메일 클라이언트 호환) ──

    private String buildEmailHtml(VerdictResponse v) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head>");
        sb.append("<body style=\"margin:0;padding:0;background:#f5f5f5;font-family:'Apple SD Gothic Neo','Malgun Gothic','Noto Sans CJK KR',sans-serif;\">");
        sb.append("<div style=\"max-width:600px;margin:0 auto;background:#ffffff;\">");

        // 상단 헤더 바
        sb.append("<div style=\"background:linear-gradient(135deg,#6366f1,#8b5cf6);padding:24px 28px;\">");
        sb.append("<h1 style=\"margin:0;color:#fff;font-size:20px;\">Homm, 살 수 있어?</h1>");
        sb.append("<p style=\"margin:6px 0 0;color:rgba(255,255,255,0.85);font-size:13px;\">청약 자금 완주 진단서</p>");
        sb.append("</div>");

        sb.append("<div style=\"padding:24px 28px;\">");

        // ── 1. 입주 완주 진단 요약 ──
        if (v.overallFundStatus() != null) {
            sb.append("<div style=\"background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:20px;margin-bottom:24px;\">");
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:0 0 12px;\">입주 완주 진단</h2>");
            sb.append("<div style=\"display:flex;gap:12px;margin-bottom:12px;\">");
            sb.append("<div style=\"flex:1;background:").append(statusBgColor(v.overallFundStatus()));
            sb.append(";border-radius:8px;padding:12px;text-align:center;\">");
            sb.append("<div style=\"font-size:11px;color:#6b7280;\">자금 계산</div>");
            sb.append("<div style=\"font-size:18px;font-weight:700;color:").append(statusTextColor(v.overallFundStatus()));
            sb.append(";\">").append(statusLabel(v.overallFundStatus())).append("</div>");
            sb.append("</div>");
            sb.append("<div style=\"flex:1;background:").append(infoConfBgColor(v.overallInfoConfidence()));
            sb.append(";border-radius:8px;padding:12px;text-align:center;\">");
            sb.append("<div style=\"font-size:11px;color:#6b7280;\">정보 확정도</div>");
            sb.append("<div style=\"font-size:18px;font-weight:700;color:").append(infoConfTextColor(v.overallInfoConfidence()));
            sb.append(";\">").append(infoConfLabel(v.overallInfoConfidence())).append("</div>");
            sb.append("</div></div>");
            if ("PARTIAL".equals(v.overallInfoConfidence())) {
                sb.append("<div style=\"font-size:12px;color:#92400e;margin-bottom:8px;\">미확정 조건은 HOLD로 별도 표시</div>");
            }
            if (v.firstShortfallStage() != null) {
                sb.append("<div style=\"font-size:13px;color:#dc2626;\">최초 자금 부족: ");
                sb.append(stageLabel(v.firstShortfallStage()));
                if (v.firstShortfallGap() != null) {
                    sb.append(" ").append(formatManWon(v.firstShortfallGap())).append(" 부족");
                }
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        // ── 대출 상품별 판정 ──
        if (v.financingRoutes() != null && !v.financingRoutes().isEmpty()) {
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:0 0 16px;padding-bottom:8px;border-bottom:2px solid #e5e7eb;\">대출 상품별 판정</h2>");
            for (FinancingRouteResponse r : v.financingRoutes()) {
                String name = r.productName() != null ? r.productName() : r.productCode();
                String badgeBg = statusBgColor(r.status());
                String badgeColor = statusTextColor(r.status());

                sb.append("<div style=\"border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-bottom:12px;\">");
                sb.append("<div style=\"display:flex;justify-content:space-between;align-items:center;\">");
                sb.append("<span style=\"font-size:15px;font-weight:600;color:#374151;\">").append(name).append("</span>");
                sb.append("<span style=\"background:").append(badgeBg).append(";color:").append(badgeColor);
                sb.append(";padding:4px 12px;border-radius:20px;font-size:13px;font-weight:600;\">").append(statusLabel(r.status())).append("</span>");
                sb.append("</div>");

                if (r.status() == VerdictStatus.OK && r.limitMax() != null) {
                    sb.append("<div style=\"margin-top:10px;color:#6b7280;font-size:13px;\">가정식 기준 예상 한도: <strong style=\"color:#1f2937;\">")
                      .append(formatLimit(r.limitMin(), r.limitMax())).append("</strong>");
                    if (r.bindingFactor() != null) {
                        sb.append(" <span style=\"color:#9ca3af;\">(").append(bindingLabel(r.bindingFactor())).append(" 기준)</span>");
                    }
                    sb.append("</div>");
                    sb.append("<div style=\"margin-top:4px;font-size:11px;color:#9ca3af;\">실제 승인 미확정 · 금융기관 심사에 따라 달라질 수 있습니다</div>");
                }
                if (r.status() == VerdictStatus.HOLD && r.reasonCode() != null) {
                    sb.append("<div style=\"margin-top:10px;background:#fffbeb;border-radius:8px;padding:10px 12px;font-size:13px;color:#92400e;\">")
                      .append(holdMessage(r.reasonCode())).append("</div>");
                }
                sb.append("</div>");
            }
        }

        // ── 계약금 / 중도금 / 잔금 단계별 결과 ──
        if (v.verdicts() != null && !v.verdicts().isEmpty()) {
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:28px 0 16px;padding-bottom:8px;border-bottom:2px solid #e5e7eb;\">계약금 · 중도금 · 잔금 단계별 결과</h2>");
            for (StageVerdictResponse s : v.verdicts()) {
                String badgeBg = statusBgColor(s.status());
                String badgeColor = statusTextColor(s.status());

                sb.append("<div style=\"border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-bottom:12px;\">");
                sb.append("<div style=\"display:flex;justify-content:space-between;align-items:center;\">");
                sb.append("<div>");
                sb.append("<span style=\"font-size:15px;font-weight:600;color:#374151;\">").append(stageLabel(s.stage())).append("</span>");
                if (s.required() != null) {
                    sb.append("&nbsp;&nbsp;<span style=\"font-size:14px;color:#6366f1;font-weight:700;\">").append(formatManWon(s.required())).append("</span>");
                }
                sb.append("</div>");
                sb.append("<span style=\"background:").append(badgeBg).append(";color:").append(badgeColor);
                sb.append(";padding:4px 12px;border-radius:20px;font-size:13px;font-weight:600;\">").append(statusLabel(s.status())).append("</span>");
                sb.append("</div>");

                if (s.available() != null) {
                    sb.append("<div style=\"margin-top:8px;font-size:13px;color:#6b7280;\">가용 금액: ").append(formatManWon(s.available())).append("</div>");
                }
                if (s.gap() != null) {
                    sb.append("<div style=\"margin-top:4px;font-size:13px;color:#dc2626;\">부족 금액: ").append(formatManWon(s.gap())).append("</div>");
                }
                if (s.scenarios() != null && !s.scenarios().isEmpty()) {
                    sb.append("<div style=\"margin-top:10px;background:#f0fdf4;border-radius:8px;padding:10px 12px;font-size:13px;color:#166534;\">");
                    for (String scenario : s.scenarios()) {
                        sb.append(scenario).append("<br>");
                    }
                    sb.append("</div>");
                }
                sb.append("</div>");
            }
        }

        // ── 대출 경로별 잔금 비교 ──
        if (v.routeComparisons() != null && !v.routeComparisons().isEmpty()) {
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:28px 0 16px;padding-bottom:8px;border-bottom:2px solid #e5e7eb;\">대출 상품별 잔금 비교</h2>");
            for (RouteBalanceComparison c : v.routeComparisons()) {
                String badgeBg = statusBgColor(c.status());
                String badgeColor = statusTextColor(c.status());

                sb.append("<div style=\"border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-bottom:12px;\">");
                sb.append("<div style=\"display:flex;justify-content:space-between;align-items:center;\">");
                sb.append("<span style=\"font-size:14px;font-weight:600;color:#374151;\">").append(c.productName()).append("</span>");
                sb.append("<span style=\"background:").append(badgeBg).append(";color:").append(badgeColor);
                sb.append(";padding:4px 12px;border-radius:20px;font-size:13px;font-weight:600;\">").append(statusLabel(c.status())).append("</span>");
                sb.append("</div>");

                sb.append("<div style=\"margin-top:8px;font-size:13px;color:#6b7280;\">");
                sb.append("대출 한도: ").append(formatManWon(c.loanLimit()));
                if (c.gap() != null) {
                    if (c.gapConservative() != null && !c.gapConservative().equals(c.gap())) {
                        sb.append(" · 예상 부족액: <span style=\"color:#dc2626;\">")
                          .append(formatManWon(c.gap())).append("~").append(formatManWon(c.gapConservative())).append("</span>");
                    } else {
                        sb.append(" · 부족: <span style=\"color:#dc2626;\">").append(formatManWon(c.gap())).append("</span>");
                    }
                } else if (c.gapConservative() != null) {
                    sb.append(" · 보수 기준 부족 가능: <span style=\"color:#dc2626;\">").append(formatManWon(c.gapConservative())).append("</span>");
                }
                sb.append("</div>");
                if (c.scenario() != null) {
                    sb.append("<div style=\"margin-top:10px;background:#f0fdf4;border-radius:8px;padding:10px 12px;font-size:13px;color:#166534;\">")
                      .append(c.scenario()).append("</div>");
                }
                sb.append("</div>");
            }
        }

        // ── 중도금 임계선 ──
        if (v.interimCriticalLine() != null) {
            InterimCriticalLineResponse cl = v.interimCriticalLine();
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:28px 0 16px;padding-bottom:8px;border-bottom:2px solid #e5e7eb;\">중도금 임계선</h2>");
            sb.append("<div style=\"border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-bottom:12px;\">");
            sb.append("<div style=\"display:flex;gap:8px;margin-bottom:8px;\">");
            sb.append("<div style=\"flex:1;text-align:center;\">");
            sb.append("<div style=\"font-size:11px;color:#6b7280;\">최소 필요 비율</div>");
            sb.append("<div style=\"font-size:16px;font-weight:700;color:#1f2937;\">").append(formatPercent(cl.criticalLoanRatio())).append("</div>");
            sb.append("</div>");
            sb.append("<div style=\"flex:1;text-align:center;\">");
            sb.append("<div style=\"font-size:11px;color:#6b7280;\">공고상 알선</div>");
            sb.append("<div style=\"font-size:16px;font-weight:700;color:#1f2937;\">").append(cl.arrangedRatio() != null ? formatPercent(cl.arrangedRatio()) : "미공시").append("</div>");
            sb.append("</div>");
            sb.append("<div style=\"flex:1;text-align:center;\">");
            sb.append("<div style=\"font-size:11px;color:#6b7280;\">안전마진</div>");
            String marginColor = "SAFE".equals(cl.safetyStatus()) ? "#065f46" : "WARNING".equals(cl.safetyStatus()) ? "#991b1b" : "#6b7280";
            sb.append("<div style=\"font-size:16px;font-weight:700;color:").append(marginColor).append(";\">")
              .append(cl.safetyMarginPp() != null ? (cl.safetyMarginPp() >= 0 ? "+" : "") + cl.safetyMarginPp() + "%p" : "-").append("</div>");
            sb.append("</div></div>");
            sb.append("<div style=\"font-size:11px;color:#9ca3af;margin-top:8px;\">").append(cl.disclaimer()).append("</div>");
            sb.append("</div>");
        }

        // ── 부족액 준비 시나리오 ──
        if (v.shortfallPreparation() != null) {
            ShortfallPreparationResponse sp = v.shortfallPreparation();
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:28px 0 16px;padding-bottom:8px;border-bottom:2px solid #e5e7eb;\">부족액 준비 시나리오</h2>");
            sb.append("<div style=\"border:1px solid #e5e7eb;border-radius:12px;padding:16px;margin-bottom:12px;\">");
            if (sp.calculable()) {
                sb.append("<div style=\"font-size:13px;color:#374151;\">예상 부족액: <strong style=\"color:#dc2626;\">").append(formatManWon(sp.totalShortfall())).append("</strong>");
                sb.append(" (").append(stageLabel(sp.shortfallStage())).append(")</div>");
                if (sp.monthsRemaining() != null) {
                    sb.append("<div style=\"margin-top:6px;font-size:13px;color:#6b7280;\">남은 준비 기간: ").append(sp.monthsRemaining()).append("개월</div>");
                }
                if (sp.monthlyRequired() != null) {
                    sb.append("<div style=\"margin-top:4px;font-size:13px;color:#6b7280;\">단순 저축 소요기간: <strong>").append(formatManWon(sp.monthlyRequired())).append("/월</strong></div>");
                    sb.append("<div style=\"margin-top:4px;font-size:11px;color:#9ca3af;\">단순 저축 소요기간이며, 실제 납부기한 내 마련 가능 여부와는 별개입니다</div>");
                }
            } else {
                sb.append("<div style=\"font-size:13px;color:#92400e;\">계산 보류: ").append(sp.holdReason()).append("</div>");
            }
            sb.append("</div>");
        }

        // ── 공고문 원문 근거 ──
        if (v.riskClauses() != null && !v.riskClauses().isEmpty()) {
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:28px 0 16px;padding-bottom:8px;border-bottom:2px solid #e5e7eb;\">공고문 위험조항 및 원문 근거</h2>");
            for (RiskClauseResponse rc : v.riskClauses()) {
                sb.append("<div style=\"border:1px solid #fde68a;border-radius:12px;padding:14px 16px;margin-bottom:10px;background:#fffbeb;\">");
                sb.append("<div style=\"font-size:13px;font-weight:600;color:#92400e;\">").append(rc.message()).append("</div>");
                if (rc.evidence() != null) {
                    for (RiskClauseResponse.PdfEvidence ev : rc.evidence()) {
                        sb.append("<div style=\"margin-top:6px;font-size:11px;color:#6b7280;border-left:3px solid #fde68a;padding-left:8px;\">");
                        if (ev.page() != null) sb.append("p.").append(ev.page()).append(" ");
                        if (ev.rawText() != null) sb.append("\"").append(ev.rawText()).append("\"");
                        sb.append("</div>");
                    }
                }
                sb.append("</div>");
            }
        }

        // ── 은행·시행사 확인 질문 ──
        if (v.interimFinancingDetail() != null
            && v.interimFinancingDetail().questionsForBankOrDeveloper() != null
            && !v.interimFinancingDetail().questionsForBankOrDeveloper().isEmpty()) {
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:28px 0 16px;padding-bottom:8px;border-bottom:2px solid #e5e7eb;\">은행·시행사 확인 질문</h2>");
            sb.append("<div style=\"border:1px solid #e5e7eb;border-radius:12px;padding:16px;\">");
            int qIdx = 1;
            for (String q : v.interimFinancingDetail().questionsForBankOrDeveloper()) {
                sb.append("<div style=\"font-size:13px;color:#374151;margin-bottom:8px;\">").append(qIdx++).append(". ").append(q).append("</div>");
            }
            sb.append("</div>");
        }

        // ── 확인 필요 항목 ──
        if (v.holds() != null && !v.holds().isEmpty()) {
            sb.append("<h2 style=\"font-size:16px;color:#1f2937;margin:28px 0 16px;padding-bottom:8px;border-bottom:2px solid #e5e7eb;\">확인 필요 항목</h2>");
            for (HoldResponse h : v.holds()) {
                String text = h.nextAction() != null ? h.nextAction() : h.message();
                if (text == null) continue;
                sb.append("<div style=\"background:#fffbeb;border:1px solid #fde68a;border-radius:12px;padding:14px 16px;margin-bottom:10px;font-size:13px;color:#92400e;\">");
                sb.append(text);
                sb.append("</div>");
            }
        }

        sb.append("</div>"); // padding div

        // ── 푸터 ──
        sb.append("<div style=\"background:#f9fafb;padding:20px 28px;border-top:1px solid #e5e7eb;\">");
        sb.append("<p style=\"margin:0;font-size:11px;color:#9ca3af;line-height:1.6;\">");
        sb.append("이 결과는 공개 자료 기준 추정이며, 최종 확정은 금융기관 심사에 따릅니다.");
        if (v.meta() != null && v.meta().calculatedAt() != null) {
            sb.append("<br>").append(v.meta().calculatedAt()).append(" 규정 기준");
        }
        sb.append("<br>Homm, 살 수 있어? - 청약 판정 서비스");
        sb.append("</p></div>");

        sb.append("</div></body></html>");
        return sb.toString();
    }

    // ── PDF용 HTML (XHTML 호환, CSS 자유롭게 사용 가능) ──

    private String buildPdfHtml(VerdictResponse v) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">");
        sb.append("<head><meta charset=\"UTF-8\" />");
        sb.append("<style>");
        sb.append("body{font-family:'SystemGothic','Apple SD Gothic Neo','Malgun Gothic','Noto Sans CJK KR',sans-serif;margin:0;padding:30px;color:#333;font-size:12px;}");
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

        // 제목
        sb.append("<h1>Homm, 살 수 있어?</h1>");
        sb.append("<p class=\"subtitle\">청약 자금 완주 진단서");
        if (v.meta() != null && v.meta().calculatedAt() != null) {
            sb.append(" · ").append(v.meta().calculatedAt()).append(" 기준");
        }
        sb.append("</p>");

        // ── 커버 메타 정보 ──
        if (v.meta() != null) {
            var m = v.meta();
            sb.append("<div class=\"card\" style=\"background:#f0f4ff;padding:14px 16px;margin-bottom:16px;\">");
            sb.append("<div class=\"card-header\"><span class=\"product-name\" style=\"font-size:13px;\">진단 대상 정보</span></div>");
            if (m.complexName() != null)
                sb.append("<div class=\"detail\">단지명: <strong>").append(m.complexName()).append("</strong></div>");
            if (m.complexId() != null)
                sb.append("<div class=\"detail\">공고번호: <strong>").append(m.complexId()).append("</strong></div>");
            if (m.unitTypeName() != null)
                sb.append("<div class=\"detail\">선택 주택형: <strong>").append(m.unitTypeName()).append("</strong></div>");
            if (m.salePriceManwon() != null)
                sb.append("<div class=\"detail\">분양가: <strong>").append(formatManWon(m.salePriceManwon())).append("</strong></div>");
            if (m.cashManwon() != null)
                sb.append("<div class=\"detail\">보유 현금: <strong>").append(formatManWon(m.cashManwon())).append("</strong></div>");
            if (m.monthlySavingManwon() != null)
                sb.append("<div class=\"detail\">월 저축액: <strong>").append(formatManWon(m.monthlySavingManwon())).append("</strong></div>");
            if (m.sourcePageCount() != null)
                sb.append("<div class=\"detail\">공고문 페이지 수: <strong>").append(m.sourcePageCount()).append("p</strong></div>");
            sb.append("<div class=\"detail\">검수 상태: <strong>").append(reviewStatusLabel(m.analysisReviewStatus())).append("</strong></div>");
            sb.append("<div class=\"detail\">산출일: <strong>").append(m.calculatedAt() != null ? m.calculatedAt() : "-").append("</strong>");
            sb.append(" / 규칙 기준일: <strong>").append(m.ruleVersion() != null ? m.ruleVersion() : "-").append("</strong></div>");
            sb.append("</div>");
        }

        // ── 1. 입주 완주 진단 요약 ──
        if (v.overallFundStatus() != null) {
            sb.append("<div class=\"card\" style=\"background:#f8fafc;padding:16px;margin-bottom:20px;\">");
            sb.append("<div class=\"card-header\"><span class=\"product-name\" style=\"font-size:14px;\">입주 완주 진단</span></div>");
            sb.append("<div class=\"detail\">자금 계산: <strong>");
            sb.append("<span class=\"badge badge-").append(statusClass(v.overallFundStatus())).append("\" style=\"float:none;\">");
            sb.append(statusLabel(v.overallFundStatus())).append("</span></strong>");
            sb.append(" / 정보 확정도: <strong>").append(infoConfLabel(v.overallInfoConfidence())).append("</strong></div>");
            // 미확정 조건 HOLD 별도 표시
            if ("PARTIAL".equals(v.overallInfoConfidence())) {
                sb.append("<div class=\"detail\" style=\"color:#92400e;\">미확정 조건은 HOLD로 별도 표시</div>");
            }
            if (v.firstShortfallStage() != null) {
                sb.append("<div class=\"detail gap-text\">최초 자금 부족: ").append(stageLabel(v.firstShortfallStage()));
                if (v.firstShortfallGap() != null) {
                    sb.append(" ").append(formatManWon(v.firstShortfallGap())).append(" 부족");
                }
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        // ── 공고문 분석 요약 ──
        if (v.analysisSummary() != null && !v.analysisSummary().isBlank()) {
            sb.append("<h2>공고문 분석 요약</h2>");
            sb.append("<div class=\"card\">");
            sb.append("<div class=\"detail\" style=\"line-height:1.6;\">").append(v.analysisSummary()).append("</div>");
            sb.append("</div>");
        }

        // ── 대출 상품별 판정 ──
        if (v.financingRoutes() != null && !v.financingRoutes().isEmpty()) {
            sb.append("<h2>대출 상품별 판정</h2>");
            for (FinancingRouteResponse r : v.financingRoutes()) {
                String name = r.productName() != null ? r.productName() : r.productCode();
                sb.append("<div class=\"card\"><div class=\"card-header clearfix\">");
                sb.append("<span class=\"product-name\">").append(name).append("</span>");
                sb.append("<span class=\"badge badge-").append(statusClass(r.status())).append("\">").append(statusLabel(r.status())).append("</span>");
                sb.append("</div>");
                if (r.status() == VerdictStatus.OK && r.limitMax() != null) {
                    sb.append("<div class=\"detail\">가정식 기준 예상 한도: <strong>").append(formatLimit(r.limitMin(), r.limitMax())).append("</strong>");
                    if (r.bindingFactor() != null) {
                        sb.append(" (").append(bindingLabel(r.bindingFactor())).append(" 기준)");
                    }
                    sb.append("</div>");
                    sb.append("<div class=\"detail\" style=\"color:#9ca3af;\">실제 승인 미확정 · 금융기관 심사에 따라 달라질 수 있습니다</div>");
                }
                if (r.status() == VerdictStatus.HOLD && r.reasonCode() != null) {
                    sb.append("<div class=\"hold-box\">").append(holdMessage(r.reasonCode())).append("</div>");
                }
                sb.append("</div>");
            }
        }

        // ── 청약 자격 판정 ──
        if (v.subscriptionEligibilities() != null && !v.subscriptionEligibilities().isEmpty()) {
            sb.append("<h2>청약 자격 판정</h2>");
            for (SubscriptionEligibilityResponse se : v.subscriptionEligibilities()) {
                sb.append("<div class=\"card\"><div class=\"card-header clearfix\">");
                sb.append("<span class=\"product-name\">").append(subscriptionTypeLabel(se.type())).append("</span>");
                sb.append("<span class=\"badge badge-").append(statusClass(se.status())).append("\">").append(subscriptionStatusLabel(se.status())).append("</span>");
                sb.append("</div>");
                if (se.status() == VerdictStatus.OK) {
                    sb.append("<div class=\"detail\" style=\"color:#9ca3af;\">입력된 일부 개인조건 기준 1차 요건 통과 · 최종 확정 아님</div>");
                }
                if (se.status() == VerdictStatus.HOLD && se.reasonCode() != null) {
                    sb.append("<div class=\"hold-box\">").append(holdMessage(se.reasonCode())).append("</div>");
                }
                sb.append("</div>");
            }
        }

        // ── 단계별 결과 ──
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

        // ── 대출 경로별 잔금 비교 ──
        if (v.routeComparisons() != null && !v.routeComparisons().isEmpty()) {
            sb.append("<h2>대출 상품별 잔금 비교</h2>");
            for (RouteBalanceComparison c : v.routeComparisons()) {
                sb.append("<div class=\"card\"><div class=\"card-header clearfix\">");
                sb.append("<span class=\"product-name\">").append(c.productName()).append("</span>");
                sb.append("<span class=\"badge badge-").append(statusClass(c.status())).append("\">").append(statusLabel(c.status())).append("</span>");
                sb.append("</div>");
                sb.append("<div class=\"detail\">대출 한도: ").append(formatManWon(c.loanLimit()));
                if (c.gap() != null) {
                    if (c.gapConservative() != null && !c.gapConservative().equals(c.gap())) {
                        sb.append(" · 예상 부족액: <span class=\"gap-text\">")
                          .append(formatManWon(c.gap())).append("~").append(formatManWon(c.gapConservative())).append("</span>");
                        sb.append("<br />보수 기준 부족액: <span class=\"gap-text\">").append(formatManWon(c.gapConservative())).append("</span>");
                    } else {
                        sb.append(" · 부족: <span class=\"gap-text\">").append(formatManWon(c.gap())).append("</span>");
                    }
                } else if (c.gapConservative() != null) {
                    // 최대 한도 기준은 충분하지만 보수적 기준에서는 부족
                    sb.append(" · 보수 기준 부족 가능: <span class=\"gap-text\">").append(formatManWon(c.gapConservative())).append("</span>");
                }
                sb.append("</div>");
                if (c.scenario() != null) {
                    sb.append("<div class=\"scenario-box\">").append(c.scenario()).append("</div>");
                }
                sb.append("</div>");
            }
        }

        // ── 중도금 임계선 ──
        if (v.interimCriticalLine() != null) {
            InterimCriticalLineResponse cl = v.interimCriticalLine();
            sb.append("<h2>중도금 임계선</h2>");
            sb.append("<div class=\"card\">");
            sb.append("<div class=\"detail\">최소 필요 비율: <strong>").append(formatPercent(cl.criticalLoanRatio())).append("</strong>");
            sb.append(" / 공고상 알선: <strong>").append(cl.arrangedRatio() != null ? formatPercent(cl.arrangedRatio()) : "미공시").append("</strong>");
            if (cl.safetyMarginPp() != null) {
                sb.append(" / 안전마진: <strong");
                if ("WARNING".equals(cl.safetyStatus())) sb.append(" class=\"gap-text\"");
                sb.append(">").append(cl.safetyMarginPp() >= 0 ? "+" : "").append(cl.safetyMarginPp()).append("%p</strong>");
            }
            sb.append("</div>");
            sb.append("<div class=\"detail\" style=\"margin-top:6px;\">").append(cl.disclaimer()).append("</div>");
            sb.append("</div>");
        }

        // ── 중도금 금융조달 상세 ──
        if (v.interimFinancingDetail() != null) {
            sb.append("<h2>중도금 금융조달 상세</h2>");
            var confirmed = v.interimFinancingDetail().confirmed();
            if (confirmed != null) {
                sb.append("<div class=\"card\">");
                sb.append("<div class=\"card-header\"><span class=\"product-name\">확인된 정보</span></div>");

                // 금액 명칭 구분 표시 — 분양가 기반 환산
                Integer salePriceForCalc = v.meta() != null ? v.meta().salePriceManwon() : null;
                if (confirmed.interimTotalRatio() != null) {
                    sb.append("<div class=\"detail\">중도금 총액: <strong>");
                    if (salePriceForCalc != null) {
                        sb.append(formatManWon((int) Math.round(confirmed.interimTotalRatio() * salePriceForCalc)));
                    }
                    sb.append(" (").append(formatPercent(confirmed.interimTotalRatio())).append(")</strong></div>");
                }
                if (confirmed.arrangedRatio() != null) {
                    sb.append("<div class=\"detail\">공고상 알선 범위: <strong>");
                    if (salePriceForCalc != null) {
                        sb.append(formatManWon((int) Math.round(confirmed.arrangedRatio() * salePriceForCalc)));
                    }
                    sb.append(" (").append(formatPercent(confirmed.arrangedRatio())).append(")</strong></div>");
                }
                if (confirmed.selfFundingRequired() != null && confirmed.selfFundingRequired()) {
                    sb.append("<div class=\"detail\">알선 범위 밖 별도 조달 대상: <strong>");
                    if (confirmed.selfFundingRatio() != null) {
                        if (salePriceForCalc != null) {
                            sb.append(formatManWon((int) Math.round(confirmed.selfFundingRatio() * salePriceForCalc)));
                        }
                        sb.append(" (").append(formatPercent(confirmed.selfFundingRatio())).append(")");
                    } else {
                        sb.append("비율 미확인");
                    }
                    sb.append("</strong> / 실제 조달 방식 미확정</div>");
                }

                if (confirmed.interimInstallmentCount() != null)
                    sb.append("<div class=\"detail\">납부 회차: <strong>").append(confirmed.interimInstallmentCount()).append("회</strong></div>");
                if (confirmed.arrangementStatus() != null)
                    sb.append("<div class=\"detail\">대출 알선: <strong>").append(arrangementLabel(confirmed.arrangementStatus())).append("</strong></div>");
                if (confirmed.interestType() != null)
                    sb.append("<div class=\"detail\">이자 방식: <strong>").append(interestTypeLabel(confirmed.interestType())).append("</strong></div>");

                // 중도금 납부일정 안내 문구
                if (confirmed.interimInstallmentCount() != null && confirmed.interimInstallmentCount() > 0
                    && confirmed.arrangedRatio() != null && confirmed.selfFundingRequired() != null && confirmed.selfFundingRequired()) {
                    sb.append("<div class=\"detail\" style=\"margin-top:8px;color:#92400e;line-height:1.5;\">");
                    sb.append("중도금 납부 일정은 확인됐지만, 사업주체 알선 ").append(formatPercent(confirmed.arrangedRatio()));
                    sb.append("와 알선 범위 밖 ");
                    if (confirmed.selfFundingRatio() != null) sb.append(formatPercent(confirmed.selfFundingRatio()));
                    else sb.append("나머지");
                    sb.append("가 각 회차에 어떻게 적용되는지는 확인되지 않았습니다. ");
                    sb.append("따라서 최초 부족 회차·날짜와 기한 내 준비 가능 여부는 계산을 보류합니다.");
                    sb.append("</div>");
                }

                sb.append("</div>");
            }
            var unconfirmed = v.interimFinancingDetail().unconfirmed();
            if (unconfirmed != null) {
                sb.append("<div class=\"card\">");
                sb.append("<div class=\"card-header\"><span class=\"product-name\">미확정 정보</span></div>");
                if (unconfirmed.bankNames() != null && !unconfirmed.bankNames().isEmpty())
                    sb.append("<div class=\"detail\">취급은행: <strong>").append(String.join(", ", unconfirmed.bankNames())).append("</strong></div>");
                else
                    sb.append("<div class=\"detail\">취급은행: <strong>미공개</strong></div>");
                if (unconfirmed.guaranteeProvider() != null)
                    sb.append("<div class=\"detail\">보증사: <strong>").append(unconfirmed.guaranteeProvider()).append("</strong></div>");
                else
                    sb.append("<div class=\"detail\">보증사: <strong>미확정</strong></div>");
                if (unconfirmed.extensionContingencyDisclosed() != null)
                    sb.append("<div class=\"detail\">연장 특약: <strong>").append(unconfirmed.extensionContingencyDisclosed() ? "공시됨" : "미공시").append("</strong></div>");
                if (unconfirmed.settlementRequirement() != null)
                    sb.append("<div class=\"detail\">정산 요건: <strong>").append(settlementLabel(unconfirmed.settlementRequirement())).append("</strong></div>");
                sb.append("</div>");
            }
        }

        // ── 부족액 준비 시나리오 ──
        if (v.shortfallPreparation() != null) {
            ShortfallPreparationResponse sp = v.shortfallPreparation();
            sb.append("<h2>부족액 준비 시나리오</h2>");
            sb.append("<div class=\"card\">");
            if (sp.calculable()) {
                sb.append("<div class=\"detail\">예상 부족액: <strong class=\"gap-text\">").append(formatManWon(sp.totalShortfall())).append("</strong>");
                sb.append(" (").append(stageLabel(sp.shortfallStage())).append(")</div>");
                if (sp.monthsRemaining() != null) sb.append("<div class=\"detail\">남은 준비 기간: ").append(sp.monthsRemaining()).append("개월</div>");
                if (sp.monthlyRequired() != null) {
                    sb.append("<div class=\"detail\">단순 저축 소요기간: <strong>").append(formatManWon(sp.monthlyRequired())).append("/월</strong></div>");
                    sb.append("<div class=\"detail\" style=\"color:#9ca3af;\">단순 저축 소요기간이며, 실제 납부기한 내 마련 가능 여부와는 별개입니다</div>");
                }
            } else {
                sb.append("<div class=\"hold-box\">계산 보류: ").append(sp.holdReason()).append("</div>");
            }
            sb.append("</div>");
        }

        // ── 자금 스트레스 시나리오 ──
        if (v.fundingStress() != null && v.fundingStress().routeCases() != null
            && !v.fundingStress().routeCases().isEmpty()) {
            sb.append("<h2>자금 스트레스 시나리오</h2>");
            for (FundingStressResponse.RouteStressCase rsc : v.fundingStress().routeCases()) {
                sb.append("<div class=\"card\">");
                sb.append("<div class=\"card-header\"><span class=\"product-name\">");
                sb.append(rsc.productName() != null ? rsc.productName() : rsc.productCode());
                sb.append(" (").append(limitCaseLabel(rsc.limitCase())).append(")");
                sb.append("</span></div>");
                if (rsc.scenarios() != null) {
                    for (FundingStressResponse.FundingScenario fs : rsc.scenarios()) {
                        String ratioText = fs.interimRatioBps() != null ? formatBps(fs.interimRatioBps()) : "-";
                        sb.append("<div class=\"detail\" style=\"margin-top:6px;\">");
                        sb.append("중도금 대출 ").append(ratioText).append(" → ");
                        if ("OK".equals(fs.status())) {
                            sb.append("<strong style=\"color:#065f46;\">완주 가능</strong>");
                        } else {
                            sb.append("<strong class=\"gap-text\">부족</strong>");
                            if (fs.firstShortfall() != null && fs.firstShortfall().shortfallManwon() != null) {
                                sb.append(" (").append(stageLabel(fs.firstShortfall().stage()));
                                sb.append(" ").append(formatManWon(fs.firstShortfall().shortfallManwon())).append(")");
                            }
                        }
                        if (fs.worstMarginManwon() != null) {
                            sb.append(" / 최대 부족: ").append(formatManWon(Math.abs(fs.worstMarginManwon())));
                        }
                        sb.append("</div>");
                    }
                }
                sb.append("</div>");
            }
        }

        // ── 공고문 원문 근거 ──
        if (v.riskClauses() != null && !v.riskClauses().isEmpty()) {
            sb.append("<h2>공고문 위험조항 및 원문 근거</h2>");
            for (RiskClauseResponse rc : v.riskClauses()) {
                sb.append("<div class=\"card\">");
                sb.append("<div class=\"hold-box\" style=\"margin-top:0;\">").append(rc.message()).append("</div>");
                if (rc.evidence() != null) {
                    for (RiskClauseResponse.PdfEvidence ev : rc.evidence()) {
                        sb.append("<div class=\"detail\" style=\"border-left:3px solid #fde68a;padding-left:6px;margin-top:4px;\">");
                        if (ev.page() != null) sb.append("p.").append(ev.page()).append(" ");
                        if (ev.rawText() != null) sb.append("\"").append(ev.rawText()).append("\"");
                        sb.append("</div>");
                    }
                }
                sb.append("</div>");
            }
        }

        // ── 은행·시행사 확인 질문 ──
        if (v.interimFinancingDetail() != null
            && v.interimFinancingDetail().questionsForBankOrDeveloper() != null
            && !v.interimFinancingDetail().questionsForBankOrDeveloper().isEmpty()) {
            sb.append("<h2>은행·시행사 확인 질문</h2>");
            sb.append("<div class=\"card\">");
            int qIdx = 1;
            for (String q : v.interimFinancingDetail().questionsForBankOrDeveloper()) {
                sb.append("<div class=\"detail\" style=\"margin-bottom:4px;\">").append(qIdx++).append(". ").append(q).append("</div>");
            }
            sb.append("</div>");
        }

        // ── 확인 필요 항목 ──
        if (v.holds() != null && !v.holds().isEmpty()) {
            sb.append("<h2>확인 필요 항목</h2>");
            for (HoldResponse h : v.holds()) {
                String text = h.nextAction() != null ? h.nextAction() : h.message();
                if (text == null) continue;
                sb.append("<div class=\"hold-box\" style=\"margin-bottom:8px;\">").append(text).append("</div>");
            }
        }

        // ── 푸터 ──
        sb.append("<div class=\"footer\">");
        sb.append("이 결과는 공개 자료 기준 추정이며, 최종 확정은 금융기관 심사에 따릅니다.<br />");
        sb.append("Homm, 살 수 있어? - 청약 자금 완주 진단서");
        sb.append("</div>");

        sb.append("</body></html>");
        return sb.toString();
    }

    // ── 유틸리티 메서드 ──

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
            case HOLD -> "확인필요";
            case GAP -> "부족";
        };
    }

    private String statusBgColor(VerdictStatus status) {
        if (status == null) return "#f3f4f6";
        return switch (status) {
            case OK -> "#d1fae5";
            case BLOCK -> "#fee2e2";
            case HOLD -> "#fef3c7";
            case GAP -> "#dbeafe";
        };
    }

    private String statusTextColor(VerdictStatus status) {
        if (status == null) return "#374151";
        return switch (status) {
            case OK -> "#065f46";
            case BLOCK -> "#991b1b";
            case HOLD -> "#92400e";
            case GAP -> "#1e40af";
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

    private String bindingLabel(String factor) {
        if (factor == null) return "";
        return switch (factor) {
            case "DTI" -> "DTI";
            case "LTV" -> "LTV";
            case "DSR" -> "DSR";
            default -> factor;
        };
    }

    private String holdMessage(String reasonCode) {
        if (reasonCode == null) return "";
        return switch (reasonCode) {
            case "NEED_SPOUSE_INCOME" -> "배우자 연소득을 입력하면 더 정확한 판정이 가능합니다.";
            case "NEED_HOUSEHOLD_INFO" -> "세대 구성, 세대원 무주택 여부, 순자산 정보를 입력해 주세요.";
            case "NEED_FIRST_TIME_INFO" -> "생애최초 여부, 세대 구성, 순자산 정보를 입력해 주세요.";
            case "NEED_NEWLYWED_INFO" -> "신혼부부 관련 정보(배우자 소득, 세대 구성 등)를 입력해 주세요.";
            case "NEED_SUBSCRIPTION_INFO" -> "청약통장 정보를 입력해 주세요.";
            case "NEED_YOUTH_NEWLYWED_INFO" -> "배우자 소득 및 청약통장 정보를 입력해 주세요.";
            case "NEED_FIRST_TIME_BUYER_INFO" -> "생애최초 주택 구입 여부를 입력해 주세요.";
            case "NEED_MONTHLY_SAVING" -> "월 저축 가능액을 입력하면 부족분 해소 시나리오를 확인할 수 있습니다.";
            default -> reasonCode;
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
            return remainder == 0 ? uk + "억원" : uk + "억 " + String.format("%,d", remainder) + "만원";
        }
        return String.format("%,d", value) + "만원";
    }

    private String formatPercent(Double ratio) {
        if (ratio == null) return "-";
        double pct = ratio * 100;
        if (pct == (int) pct) return (int) pct + "%";
        return String.format("%.1f%%", pct);
    }

    private String infoConfLabel(String confidence) {
        if (confidence == null) return "-";
        return switch (confidence) {
            case "CONFIRMED" -> "확정";
            case "PARTIAL" -> "일부 확인";
            case "HOLD" -> "미확정";
            default -> confidence;
        };
    }

    private String subscriptionTypeLabel(String type) {
        if (type == null) return "-";
        return switch (type) {
            case "SUB_FIRST" -> "생애최초 특별공급";
            case "SUB_GENERAL" -> "일반공급";
            case "SUB_NEWLYWED" -> "신혼부부 특별공급";
            default -> type;
        };
    }

    private String arrangementLabel(String status) {
        if (status == null) return "-";
        return switch (status) {
            case "CONFIRMED" -> "확정";
            case "PLANNED" -> "알선 예정";
            case "NOT_AVAILABLE" -> "불가";
            case "NOT_STATED" -> "미기재";
            default -> status;
        };
    }

    private String interestTypeLabel(String type) {
        if (type == null) return "-";
        return switch (type) {
            case "DEFERRED_INTEREST" -> "이자후불제";
            case "PREPAID_INTEREST" -> "이자선납";
            default -> type;
        };
    }

    private String settlementLabel(String requirement) {
        if (requirement == null) return "-";
        return switch (requirement) {
            case "REPAY_OR_CONVERT_TO_MORTGAGE" -> "상환 또는 담보대출 전환";
            case "FULL_REPAY" -> "전액 상환";
            default -> requirement;
        };
    }

    private String limitCaseLabel(String limitCase) {
        if (limitCase == null) return "-";
        return switch (limitCase) {
            case "CONSERVATIVE_LIMIT" -> "보수적 한도";
            case "MAXIMUM_LIMIT" -> "최대 한도";
            default -> limitCase;
        };
    }

    private String formatBps(Integer bps) {
        if (bps == null) return "-";
        double pct = bps / 100.0;
        if (pct == (int) pct) return (int) pct + "%";
        return String.format("%.1f%%", pct);
    }

    private String reviewStatusLabel(String status) {
        if (status == null) return "AI 분석 없음";
        return switch (status) {
            case "REVIEWED" -> "검수 완료(REVIEWED)";
            case "AUTO_EXTRACTED" -> "AI 추출 · 검수 대기(AUTO_EXTRACTED)";
            default -> status;
        };
    }

    private String subscriptionStatusLabel(VerdictStatus status) {
        if (status == null) return "-";
        return switch (status) {
            case OK -> "1차 요건 통과";
            case BLOCK -> "불가";
            case HOLD -> "확인필요";
            case GAP -> "부족";
        };
    }

    private String infoConfBgColor(String confidence) {
        if (confidence == null) return "#f3f4f6";
        return switch (confidence) {
            case "CONFIRMED" -> "#d1fae5";
            case "PARTIAL" -> "#dbeafe";
            case "HOLD" -> "#fef3c7";
            default -> "#f3f4f6";
        };
    }

    private String infoConfTextColor(String confidence) {
        if (confidence == null) return "#374151";
        return switch (confidence) {
            case "CONFIRMED" -> "#065f46";
            case "PARTIAL" -> "#1e40af";
            case "HOLD" -> "#92400e";
            default -> "#374151";
        };
    }
}