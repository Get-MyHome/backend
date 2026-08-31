package aichallenge.getmyhome.verdict.controller;

import aichallenge.getmyhome.global.success.GlobalSuccessCode;
import aichallenge.getmyhome.global.success.SuccessResponse;
import aichallenge.getmyhome.verdict.dto.req.VerdictEmailRequest;
import aichallenge.getmyhome.verdict.dto.req.VerdictRequest;
import aichallenge.getmyhome.verdict.dto.res.VerdictEmailResponse;
import aichallenge.getmyhome.verdict.dto.res.VerdictResponse;
import aichallenge.getmyhome.verdict.service.VerdictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @PostMapping
    public ResponseEntity<SuccessResponse<VerdictResponse>> calculate(@Valid @RequestBody VerdictRequest request) {
        return SuccessResponse.of(GlobalSuccessCode.SUCCESS, verdictService.calculate(request));
    }

    @Operation(summary = "판정 결과 이메일 발송", description = "지정된 판정 결과를 이메일로 발송합니다.")
    @PostMapping("/{verdictId}/email")
    public ResponseEntity<SuccessResponse<VerdictEmailResponse>> sendEmail(
            @Parameter(description = "판정 고유 ID", example = "V-a1b2c3d4")
            @PathVariable String verdictId,
            @Valid @RequestBody VerdictEmailRequest request) {
        return SuccessResponse.of(GlobalSuccessCode.SUCCESS,
                verdictService.sendResultEmail(verdictId, request.email()));
    }
}