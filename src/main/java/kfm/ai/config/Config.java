package kfm.ai.config;

import kfm.ai.tool.DateTimeTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {

    /** Creates the ChatClient bean wired with the default system prompt and tools. */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, DateTimeTools dateTimeTools) {
        return ChatClient
          .builder(chatModel)
          .defaultSystem("You are a helpful assistant. Answer questions clearly and concisely.")
          .defaultTools(dateTimeTools)
          .build();
    }
}
