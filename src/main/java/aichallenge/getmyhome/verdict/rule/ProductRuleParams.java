package aichallenge.getmyhome.verdict.rule;

import lombok.Getter;
import lombok.Setter;

/** 상품 단위 규칙 파라미터 (yml에서 바인딩) */
@Getter
@Setter
public class ProductRuleParams {

  private int incomeLimit;        // 부부합산 소득 상한 (만 원)
  private int housingPriceLimit;  // 대상 주택가격 상한 (만 원)
  private int loanCap;            // 상품 절대 한도 (만 원)
  private int baseLtvPercent;     // 기본 LTV (%)
  private int dtiPercent;         // DTI 한도 (%)
  private double policyRate;      // 정책대출 기본 금리 (%)
}
