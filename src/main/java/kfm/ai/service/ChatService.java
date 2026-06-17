package kfm.ai.service;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public interface ChatService {

    record ChatRequest(@Nullable UUID chatId, String question) {}

    record ChatResponse(UUID chatId, String answer) {}
    
    ChatResponse chat(ChatRequest chatRequest);
}
