package aichallenge.getmyhome.complex.controller;

import aichallenge.getmyhome.complex.enums.HouseCategory;
import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse;
import aichallenge.getmyhome.complex.service.ComplexService;
import aichallenge.getmyhome.global.success.GlobalSuccessCode;
import aichallenge.getmyhome.global.success.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "청약 공고", description = "청약 공고 목록 조회 및 상세 조회 API")
@RestController
@RequestMapping("/complexes")
@RequiredArgsConstructor
public class ComplexController {

    private final ComplexService complexService;

    @Operation(summary = "청약 공고 목록 조회", description = "지역별 청약 공고 목록을 페이지네이션으로 조회합니다. 정렬 순서는 청약홈 API 기본 정렬을 따릅니다.")
    @GetMapping
    public ResponseEntity<SuccessResponse<ComplexListResponse>> getComplexes(
            @Parameter(description = "공급지역 필터. 미입력 시 전국 조회. "
                    + "허용 값: 서울, 경기도, 인천, 부산, 대구, 광주, 대전, 울산, 세종, "
                    + "강원, 충북, 충남, 전북, 전남, 경북, 경남, 제주. "
                    + "주의: 경기 지역만 '경기도'로 입력해야 합니다 (다른 지역은 약어 사용).",
                    example = "서울")
            @RequestParam(required = false) String region,
            @Parameter(description = "주택 구분 필터. 미입력 시 전체 조회. "
                    + "PUBLIC: 공공(국민주택), PRIVATE: 민간(민영주택)",
                    example = "PRIVATE")
            @RequestParam(required = false) HouseCategory houseCategory,
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지당 항목 수", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return SuccessResponse.of(GlobalSuccessCode.SUCCESS,
                complexService.getComplexes(region, houseCategory, page, size));
    }

    @Operation(summary = "청약 공고 상세 조회", description = "특정 청약 공고의 상세 정보와 주택형(평형) 목록을 조회합니다.")
    @GetMapping("/{complexId}")
    public ResponseEntity<SuccessResponse<ComplexDetailResponse>> getComplexDetail(
            @Parameter(description = "공고 관리 번호. GET /complexes 목록 응답의 complexId 값을 사용합니다.", example = "2025000001")
            @PathVariable String complexId) {
        return SuccessResponse.of(GlobalSuccessCode.SUCCESS,
                complexService.getComplexDetail(complexId));
    }
}