package com.codeassistant.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "oldPassword 不能为空")
        @Size(min = 6, max = 64, message = "oldPassword 长度需在 6 到 64 之间")
        String oldPassword,
        @NotBlank(message = "newPassword 不能为空")
        @Size(min = 6, max = 64, message = "newPassword 长度需在 6 到 64 之间")
        String newPassword
) {
}
