package aichallenge.getmyhome.verdict.controller;

import aichallenge.getmyhome.global.success.GlobalSuccessCode;
import aichallenge.getmyhome.global.success.SuccessResponse;
import aichallenge.getmyhome.verdict.dto.req.VerdictEmailRequest;
import aichallenge.getmyhome.verdict.dto.req.VerdictRequest;
import aichallenge.getmyhome.verdict.dto.res.VerdictEmailResponse;
import aichallenge.getmyhome.verdict.dto.res.VerdictResponse;
import aichallenge.getmyhome.verdict.service.VerdictService;
import aichallenge.getmyhome.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "청약 판정", description = "사용자 조건 기반 청약 자격·자금 경로·구간 판정 API")
@RestController
@RequestMapping("/verdicts")
@RequiredArgsConstructor
public class VerdictController {

    private final VerdictService verdictService;

    @Operation(summary = "청약 판정 실행", description = "사용자 조건과 선택한 단지 정보를 기반으로 자금 경로, 청약 자격, 구간 판정을 수행합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판정 성공",
            useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "요청 데이터 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "유효성 검증 실패", value = """
                        {"errorCode":"REQUEST_001","message":"요청 데이터가 유효하지 않습니다. 입력값을 확인해 주세요.","retryable":false}"""),
                    @ExampleObject(name = "토큰 만료", value = """
                        {"errorCode":"VERDICT_005","message":"조건 토큰이 만료되었거나 존재하지 않습니다. 대출 자격 조회를 다시 수행해 주세요.","retryable":false}"""),
                    @ExampleObject(name = "조건 누락", value = """
                        {"errorCode":"VERDICT_007","message":"conditionToken 또는 user 중 하나는 필수입니다.","retryable":false}""")
                })),
        @ApiResponse(responseCode = "500", description = "판정 계산 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"errorCode":"VERDICT_002","message":"판정 계산 중 오류가 발생했습니다.","retryable":false}""")))
    })
    @PostMapping
    public ResponseEntity<SuccessResponse<VerdictResponse>> calculate(@Valid @RequestBody VerdictRequest request) {
        return SuccessResponse.of(GlobalSuccessCode.SUCCESS, verdictService.calculate(request));
    }

    @Operation(summary = "판정 결과 이메일 발송", description = "지정된 판정 결과를 이메일로 발송합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "발송 성공",
            useReturnTypeSchema = true),
        @ApiResponse(responseCode = "404", description = "판정 결과 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"errorCode":"VERDICT_003","message":"판정 결과가 만료되었거나 존재하지 않습니다.","retryable":false}"""))),
        @ApiResponse(responseCode = "500", description = "이메일 발송 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = """
                    {"errorCode":"VERDICT_006","message":"이메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.","retryable":true}""")))
    })
    @PostMapping("/{verdictId}/email")
    public ResponseEntity<SuccessResponse<VerdictEmailResponse>> sendEmail(
            @Parameter(description = "판정 고유 ID", example = "V-a1b2c3d4")
            @PathVariable String verdictId,
            @Valid @RequestBody VerdictEmailRequest request) {
        return SuccessResponse.of(GlobalSuccessCode.SUCCESS,
                verdictService.sendResultEmail(verdictId, request.email()));
    }
}