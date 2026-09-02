package aichallenge.getmyhome.verdict.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(title = "RiskClauseResponse", description = "공고문 위험조항. AI가 공고문에서 추출한 사용자 자금에 영향을 줄 수 있는 조항")
public record RiskClauseResponse(
  @Schema(description = "위험조항 코드. "
      + "LOAN_MEDIATION_NOT_GUARANTEED(대출 알선 비보장), "
      + "INDIVIDUAL_REVIEW_REQUIRED(개인심사 필요), "
      + "SELF_FUNDING_REQUIRED(자납 필요), "
      + "INTEREST_PAYMENT_RISK(이자 부담 위험), "
      + "LOAN_NOT_AVAILABLE(대출 불가), "
      + "TERMS_DIFFER_BY_HOUSING_TYPE(주택형별 조건 상이)",
      example = "LOAN_MEDIATION_NOT_GUARANTEED")
  String code,
  @Schema(description = "영향 구간. CONTRACT(계약금), INTERIM(중도금), BALANCE(잔금)", example = "INTERIM")
  String impactStage,
  @Schema(description = "사용자 안내 메시지 (고정 템플릿)", example = "사업주체의 중도금 대출 알선은 실제 실행을 보장하지 않습니다.")
  String message,
  @Schema(description = "다음 행동 지침 (고정 템플릿)", example = "알선 확정 여부와 불가 시 직접 납부 일정을 시행사에 확인하세요.")
  String nextAction,
  @Schema(description = "해당 위험조항의 PDF 원문 근거 목록")
  List<PdfEvidence> evidence
) {

  @Schema(title = "PdfEvidence", description = "PDF 원문 근거")
  public record PdfEvidence(
    @Schema(description = "PDF 물리 페이지 번호", example = "7")
    Integer page,
    @Schema(description = "공고문 원문 텍스트", example = "대출 알선이 불가할 수 있으며 이는 사업주체의 의무사항이 아님")
    String rawText
  ) {}
}
