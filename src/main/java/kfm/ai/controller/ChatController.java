package kfm.ai.controller;

import kfm.ai.service.ChatService;
import kfm.ai.service.ChatService.ChatRequest;
import kfm.ai.service.ChatService.ChatResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@Slf4j  
public class ChatController {
    
    private ChatService chatService;
    
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** Processes a chat request and returns the AI-generated response. */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest chatRequest) {
        log.debug("Received chat request:  {}", chatRequest);
        ChatResponse chatResponse = chatService.chat(chatRequest);
        return ResponseEntity.ok(chatResponse);
    }
}
