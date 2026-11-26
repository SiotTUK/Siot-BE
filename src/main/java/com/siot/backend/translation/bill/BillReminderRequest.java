package com.siot.backend.translation.bill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** RUL-07 납부 마감 알림 켜기·끄기 */
@Schema(description = "납부 마감 알림 설정")
public record BillReminderRequest(
        @Schema(description = "true 면 납부 기한 D-7·D-1 에 알림", example = "true") @NotNull Boolean enabled) {
}
