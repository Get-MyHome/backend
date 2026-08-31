package aichallenge.getmyhome.verdict.dto.req;

import aichallenge.getmyhome.verdict.enums.HouseholdType;
import aichallenge.getmyhome.verdict.enums.IncomeType;
import aichallenge.getmyhome.verdict.enums.MaritalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "사용자 조건 입력. 금액 단위: 만 원 정수")
public record UserConditionRequest(

  // ── 1단계 필수 ──
  @Schema(description = "본인 연소득 (만 원)", example = "4000")
  @NotNull(message = "연소득을 입력해 주세요.")
  Integer annualIncome,

  @Schema(description = "보유 현금 (만 원)", example = "5000")
  @NotNull(message = "보유 자금을 입력해 주세요.")
  Integer cash,

  @Schema(description = "생년월일", example = "1995-03-15")
  @NotNull(message = "생년월일을 입력해 주세요.")
  LocalDate birthDate,

  @Schema(description = "혼인 상태")
  @NotNull(message = "혼인 상태를 선택해 주세요.")
  MaritalStatus marital,

  @Schema(description = "무주택 여부", example = "true")
  @NotNull(message = "무주택 여부를 선택해 주세요.")
  Boolean homeless,

  @Schema(description = "전세보증금을 보유 자금에 포함할지 여부", example = "false")
  Boolean includeDepositAsCash,

  // ── 2단계 선택 ──
  @Schema(description = "소득 형태")
  IncomeType incomeType,

  @Schema(description = "월 저축 가능액 (만 원)", example = "100")
  Integer monthlySaving,

  @Schema(description = "배우자·예비배우자 연소득 (만 원)", example = "3000")
  Integer spouseIncome,

  @Schema(description = "결혼 예정일 (YYYY-MM)", example = "2027-05")
  String marriagePlannedDate,

  @Schema(description = "기존 대출 월 상환액 (만 원)", example = "50")
  Integer existingLoanMonthlyPayment,

  @Schema(description = "기존 대출 잔액 (만 원)", example = "3000")
  Integer existingLoanBalance,

  @Schema(description = "세대 구성")
  HouseholdType householdType,

  @Schema(description = "세대원 전원 무주택 여부", example = "true")
  Boolean allMembersHomeless,

  @Schema(description = "순자산 - 본인·배우자 합산 (만 원)", example = "29200")
  Integer netAsset,

  @Schema(description = "청약 당첨권 보유 여부", example = "false")
  Boolean hasSubscriptionRight,

  @Schema(description = "생애최초 주택 구입 여부", example = "true")
  Boolean firstTimeBuyer,

  @Schema(description = "청약통장 정보")
  SubscriptionAccount subscriptionAccount
) {

  @Schema(description = "청약통장 정보")
  public record SubscriptionAccount(
    @Schema(description = "통장 종류", example = "YOUTH_DREAM")
    String type,
    @Schema(description = "가입일", example = "2023-01-10")
    LocalDate openedAt,
    @Schema(description = "납입 횟수", example = "24")
    Integer depositCount,
    @Schema(description = "납입 총액 (만 원)", example = "600")
    Integer depositAmount
  ) {
  }
}