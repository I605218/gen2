package com.codeassistant.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "nickname 不能为空")
        @Size(max = 64, message = "nickname 长度不能超过 64")
        String nickname
) {
}
