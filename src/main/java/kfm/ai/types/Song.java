package kfm.ai.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Song {

    private String title;
    
    private String lyrics;

    /**
     * Performance-specific note extracted from parenthetical text in the song entry,
     * excluding the surrounding parentheses. Null when no annotation is present.
     * Multiple parenthetical phrases are concatenated in DOM order separated by a single space.
     */
    private String annotation;

    /**
     * True when a '>' segue marker was present adjacent to this song entry,
     * indicating the music continued directly into the next song without stopping.
     */
    @Builder.Default
    private boolean segue = false;
    
}
