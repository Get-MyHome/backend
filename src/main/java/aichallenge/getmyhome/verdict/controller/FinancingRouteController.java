package aichallenge.getmyhome.verdict.controller;

import aichallenge.getmyhome.global.success.GlobalSuccessCode;
import aichallenge.getmyhome.global.success.SuccessResponse;
import aichallenge.getmyhome.verdict.dto.req.UserConditionRequest;
import aichallenge.getmyhome.verdict.dto.res.FinancingRouteResultResponse;
import aichallenge.getmyhome.verdict.rule.RuleProperties;
import aichallenge.getmyhome.verdict.rule.RuleVersion;
import aichallenge.getmyhome.verdict.service.FinancingRouteService;
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

@Tag(name = "대출 자격 조회", description = "사용자 조건 기반 대출 상품 자격 및 탈락 사유 조회 API")
@RestController
@RequestMapping("/financing-routes")
@RequiredArgsConstructor
public class FinancingRouteController {

    private final FinancingRouteService financingRouteService;
    private final RuleProperties ruleProperties;

    @Operation(
        summary = "대출 상품 자격 조회",
        description = "사용자 조건을 입력하면 6개 대출 상품별 자격 여부, 한도, 탈락 사유를 반환합니다. "
            + "응답에 포함된 conditionToken을 공고 매칭 조회 시 전달하면 사용자 조건을 재전송하지 않아도 됩니다.\n\n"
            + "**Request:** `UserConditionRequest` · **Response data:** `FinancingRouteResultResponse`"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공 — data: FinancingRouteResultResponse",
            useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "요청 데이터 오류 — 필수 필드(annualIncome, cash, birthDate, marital, homeless, includeDepositAsCash) 누락 시 발생",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"error_code":"REQUEST_001","message":"요청 데이터가 유효하지 않습니다. 입력값을 확인해 주세요.","retryable":false}"""))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"error_code":"RESPONSE_001","message":"서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.","retryable":true}""")))
    })
    @PostMapping
    public ResponseEntity<SuccessResponse<FinancingRouteResultResponse>> getFinancingRoutes(
            @Valid @RequestBody UserConditionRequest request) {
        RuleVersion rule = ruleProperties.resolve(null);
        FinancingRouteResultResponse result = financingRouteService.evaluateAndCache(request, rule);
        return SuccessResponse.of(GlobalSuccessCode.SUCCESS, result);
    }
}