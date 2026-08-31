package aichallenge.getmyhome.verdict.rule;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/** 하나의 rule_version에 대응하는 전체 규칙 파라미터 세트 */
@Getter
@Setter
public class RuleVersion {

  private String assumptionSetId;       // 가정 세트 ID (응답 meta에 포함)
  private int netAssetLimit;            // 디딤돌 계열 순자산 상한 (만 원)
  private int singleHeadMinAge;         // 미혼 단독세대주 최소 나이
  private int singleHeadLoanCap;        // 미혼 단독세대주 대출 한도 (만 원)
  private int singleHeadFirstLoanCap;   // 미혼 단독세대주 생애최초 대출 한도 (만 원)
  private double bankRateMin;           // 은행 주담대 최저 금리 (%)
  private double bankRateMax;           // 은행 주담대 최고 금리 (%)
  private double stressDsrRate;         // 스트레스 DSR 적용 금리 (%)
  private int bankDsrPercent;           // 은행 DSR 한도 (%)
  private int loanTermYears;            // 대출 기간 (년)
  private int youthDreamMaxAge;         // 청년주택드림 나이 상한 (만 나이)
  private int subscriptionMinMonths;    // 청약통장 최소 가입 개월 수
  private Map<String, ProductRuleParams> products;  // 상품별 파라미터

  public ProductRuleParams getProduct(String productCode) {
    ProductRuleParams params = products.get(productCode);
    if (params == null) {
      throw new IllegalStateException(
        "규칙 버전에 상품 코드 '" + productCode + "' 설정이 존재하지 않습니다.");
    }
    return params;
  }
}
