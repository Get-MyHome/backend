package aichallenge.getmyhome.complex.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "지역별 공고 수 응답 (진행 중 + 최근 마감 30일 포함)")
public record RegionCountResponse(
  @Schema(description = "전체 공고 수 (진행 중 + 최근 마감 30일)", example = "34")
  int total,
  @Schema(description = "지역별 공고 수 목록")
  List<RegionCount> regions,
  @Schema(description = "데이터 업데이트 시간 (yyyy-MM-dd HH:mm:ss)", example = "2026-09-04 03:30:00")
  String updatedAt
) {

  @Schema(description = "지역별 공고 수")
  public record RegionCount(
    @Schema(description = "지역명", example = "서울")
    String region,
    @Schema(description = "해당 지역 공고 수", example = "12")
    int count
  ) {
  }
}