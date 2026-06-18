package kfm.ai.service.impl;

import kfm.ai.service.ChatService;
import kfm.ai.tool.DateTimeTools;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@SessionScope
public class ChatServiceImpl implements ChatService {

    private ChatClient chatClient;
    private DateTimeTools dateTimeTools;

    public ChatServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.dateTimeTools = new DateTimeTools();
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        UUID chatId = Optional
          .ofNullable(chatRequest.chatId())
          .orElse(UUID.randomUUID());
        log.debug("Chat ID: {}", chatId);
        log.debug("Question: {}", chatRequest.question());

        String answer = chatClient
          .prompt()
          .user(chatRequest.question())
          .call()
          .content();

        answer = executeToolIfCalled(answer);
        log.debug("Answer: {}", answer);
        return new ChatResponse(chatId, answer);
    }

    private String executeToolIfCalled(String response) {
        if (response.contains("getCurrentDateTime")) {
            log.debug("Tool call detected: getCurrentDateTime");
            String toolResult = dateTimeTools.getCurrentDateTime();
            log.debug("Tool result: {}", toolResult);
            return "The current date and time is: " + toolResult;
        }
        return response;
    }
}
