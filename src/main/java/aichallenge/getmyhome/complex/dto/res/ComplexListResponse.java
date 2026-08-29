package aichallenge.getmyhome.complex.dto.res;

import java.util.List;

/**
 * GET /complexes 응답 — 청약 공고 목록
 */
public record ComplexListResponse(
  List<ComplexSummary> items,
  int total,
  int page,
  int size
) {

  public record ComplexSummary(
    String complexId,
    String name,
    String houseType,
    String region,
    String address,
    String announcementDate,
    String applicationEndDate,
    String expectedMoveIn,
    boolean isJudgeable
  ) {
  }
}