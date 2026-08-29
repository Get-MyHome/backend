package aichallenge.getmyhome.complex.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 청약홈 API 공통 응답 래퍼 — 모든 API가 이 형태로 응답한다
 *
 * @param currentCount 현재 페이지에 포함된 데이터 건수
 * @param data         실제 데이터 목록 (API마다 타입이 다름)
 * @param matchCount   검색 조건에 매칭된 전체 건수
 * @param page         현재 페이지 번호
 * @param perPage      페이지당 건수
 * @param totalCount   전체 데이터 건수
 */
public record ApplyhomeApiResponse<T>(
        @JsonProperty("currentCount") int currentCount,
        @JsonProperty("data") List<T> data,
        @JsonProperty("matchCount") int matchCount,
        @JsonProperty("page") int page,
        @JsonProperty("perPage") int perPage,
        @JsonProperty("totalCount") int totalCount
) {
}