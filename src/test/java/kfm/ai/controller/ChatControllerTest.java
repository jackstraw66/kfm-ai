package kfm.ai.controller;

import kfm.ai.service.ChatService;
import kfm.ai.service.ChatService.ChatRequest;
import kfm.ai.service.ChatService.ChatResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChatController}.
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatService);
    }

    @Test
    void chat_delegatesToService_returnsOk() {
        UUID chatId = UUID.randomUUID();
        ChatRequest request = new ChatRequest(chatId, "What time is it?");
        ChatResponse serviceResponse = new ChatResponse(chatId, "It is noon.");

        when(chatService.chat(any(ChatRequest.class))).thenReturn(serviceResponse);

        ResponseEntity<ChatResponse> response = controller.chat(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(serviceResponse, response.getBody());
        verify(chatService).chat(request);
    }

    @Test
    void chat_nullChatId_stillDelegates() {
        ChatRequest request = new ChatRequest(null, "Hello");
        UUID generatedId = UUID.randomUUID();
        ChatResponse serviceResponse = new ChatResponse(generatedId, "Hi there!");

        when(chatService.chat(any(ChatRequest.class))).thenReturn(serviceResponse);

        ResponseEntity<ChatResponse> response = controller.chat(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(generatedId, response.getBody().chatId());
    }
}
