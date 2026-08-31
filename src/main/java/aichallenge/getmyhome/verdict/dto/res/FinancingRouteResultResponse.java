package aichallenge.getmyhome.verdict.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(title = "FinancingRouteResultResponse", description = "대출 상품 자격 조회 결과 (조건 토큰 포함)")
public record FinancingRouteResultResponse(
  @Schema(description = "조건 토큰. 공고 매칭 조회 시 이 값을 전달하면 사용자 조건을 재전송하지 않아도 됩니다.",
          example = "CT-a1b2c3d4")
  String conditionToken,
  @Schema(description = "대출 상품별 판정 결과 목록")
  List<FinancingRouteDetailResponse> routes
) {
}
