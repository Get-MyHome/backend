package aichallenge.getmyhome.verdict.enums;

import aichallenge.getmyhome.verdict.dto.res.EvidenceResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum EvidenceRegistry {

  EV_RULE_001("EV-RULE-001", "규정", "주택도시기금 수치 기준표", "2026-08-20", null),
  EV_RULE_002("EV-RULE-002", "규정", "디딤돌대출 자격요건", "2026-08-20", null),
  EV_RULE_003("EV-RULE-003", "규정", "청년주택드림 대출 요건", "2026-08-20", null),
  EV_RULE_004("EV-RULE-004", "규정", "은행 주담대 DSR 규제", "2026-08-20", null),
  EV_RULE_005("EV-RULE-005", "규정", "일반공급 청약 자격 기준", "2026-08-20", null),
  EV_RULE_006("EV-RULE-006", "규정", "신혼부부 특별공급 요건 (주택공급에 관한 규칙 제35조)", "2026-08-20", null),
  EV_RULE_007("EV-RULE-007", "규정", "생애최초 특별공급 요건 (주택공급에 관한 규칙 제36조)", "2026-08-20", null),
  EV_CALC_001("EV-CALC-001", "계산", "구간별 필요자금 산출", "2026-08-20", null),
  EV_SRC_001("EV-SRC-001", "공고문", "분양 공고문 PDF 분석 결과", null, null);

  private final String evidenceId;
  private final String sourceType;
  private final String ref;
  private final String asOf;
  private final String url;

  public EvidenceResponse toEvidenceResponse() {
    return new EvidenceResponse(evidenceId, sourceType, ref, asOf, url);
  }

  public static void addIfAbsent(List<EvidenceResponse> evidence, EvidenceRegistry item) {
    boolean exists = evidence.stream()
      .anyMatch(e -> item.evidenceId.equals(e.evidenceId()));
    if (!exists) {
      evidence.add(item.toEvidenceResponse());
    }
  }
}
