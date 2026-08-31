package aichallenge.getmyhome.verdict.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소득 형태")
public enum IncomeType {
  @Schema(description = "근로소득")
  SALARY,
  @Schema(description = "사업소득")
  BUSINESS
}