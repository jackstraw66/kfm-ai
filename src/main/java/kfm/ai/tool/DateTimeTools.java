package kfm.ai.tool;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class DateTimeTools {

    @Tool(description = "Get the current date and time in the user's timezone")
    public String getCurrentDateTime() {
        log.debug("Calculating date/time ...");
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }
}
