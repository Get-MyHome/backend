package aichallenge.getmyhome.complex.controller;

import aichallenge.getmyhome.complex.dto.req.MatchedComplexRequest;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse;
import aichallenge.getmyhome.complex.service.ComplexService;
import aichallenge.getmyhome.global.success.GlobalSuccessCode;
import aichallenge.getmyhome.global.success.SuccessResponse;
import aichallenge.getmyhome.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "청약 공고 매칭", description = "사용자 조건 기반 대출 매칭 공고 조회 API")
@RestController
@RequestMapping("/complexes")
@RequiredArgsConstructor
public class MatchedComplexController {

    private final ComplexService complexService;

    @Operation(
        summary = "사용자 조건 기반 공고 매칭 조회",
        description = "conditionToken 또는 user 중 하나로 사용자 조건을 전달합니다. "
            + "토큰이 있으면 캐시된 조건을 사용하고, 없으면 user를 직접 사용합니다. "
            + "각 공고의 분양가 기반 대출 매칭 결과를 반환합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "요청 데이터 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "토큰 만료", value = """
                        {"errorCode":"VERDICT_005","message":"조건 토큰이 만료되었거나 존재하지 않습니다. 대출 자격 조회를 다시 수행해 주세요.","retryable":false}"""),
                    @ExampleObject(name = "조건 누락", value = """
                        {"errorCode":"VERDICT_007","message":"conditionToken 또는 user 중 하나는 필수입니다.","retryable":false}""")
                })),
        @ApiResponse(responseCode = "502", description = "청약홈 API 호출 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"errorCode":"APPLYHOME_001","message":"청약홈 API 호출에 실패했습니다. 잠시 후 다시 시도해 주세요.","retryable":true}"""))),
        @ApiResponse(responseCode = "504", description = "청약홈 API 타임아웃",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"errorCode":"APPLYHOME_003","message":"청약홈 API 응답 시간이 초과되었습니다.","retryable":true}""")))
    })
    @PostMapping("/matched")
    public ResponseEntity<SuccessResponse<ComplexListResponse>> getMatchedComplexes(
            @Valid @RequestBody MatchedComplexRequest request) {
        return SuccessResponse.of(GlobalSuccessCode.SUCCESS,
            complexService.getMatchedComplexes(
                request.conditionToken(), request.user(),
                request.region(), request.houseCategory(),
                request.pageOrDefault(), request.sizeOrDefault()));
    }
}
