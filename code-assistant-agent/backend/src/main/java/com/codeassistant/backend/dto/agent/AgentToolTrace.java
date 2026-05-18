package com.codeassistant.backend.dto.agent;

import java.util.List;

public record AgentToolTrace(
        String name,
        String input,
        String output
) {
}
