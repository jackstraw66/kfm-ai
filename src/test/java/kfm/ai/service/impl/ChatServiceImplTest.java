package kfm.ai.service.impl;

import kfm.ai.service.ChatService.ChatRequest;
import kfm.ai.service.ChatService.ChatResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChatServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec callResponseSpec;

    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(chatClient);
    }

    @Test
    void chat_withChatId_preservesId() {
        UUID chatId = UUID.randomUUID();
        ChatRequest request = new ChatRequest(chatId, "Hello");

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Hi there!");

        ChatResponse response = chatService.chat(request);

        assertEquals(chatId, response.chatId());
        assertEquals("Hi there!", response.answer());
    }

    @Test
    void chat_nullChatId_generatesNewId() {
        ChatRequest request = new ChatRequest(null, "Hello");

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Response");

        ChatResponse response = chatService.chat(request);

        assertNotNull(response.chatId());
        assertEquals("Response", response.answer());
    }

    @Test
    void chat_responseContainsToolCall_executesTool() {
        ChatRequest request = new ChatRequest(UUID.randomUUID(), "What time?");

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("I'll call getCurrentDateTime for you");

        ChatResponse response = chatService.chat(request);

        assertTrue(response.answer().startsWith("The current date and time is:"));
    }

    @Test
    void chat_responseWithoutToolCall_returnsDirectly() {
        ChatRequest request = new ChatRequest(UUID.randomUUID(), "Tell me a joke");

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Why did the chicken cross the road?");

        ChatResponse response = chatService.chat(request);

        assertEquals("Why did the chicken cross the road?", response.answer());
    }
}
