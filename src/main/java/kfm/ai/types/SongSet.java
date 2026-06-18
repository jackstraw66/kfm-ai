package kfm.ai.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SongSet {

    /**
     * The set number.  e.g.  Set I or Set II, etc.
     */
    private int ordinal;
    
    private List<Song> songs;

    /**
     * True when this set block was labeled as an encore (label matches ^Encore.* case-insensitively).
     * All sets (regular and encore) appear in the same songSets list in DOM order.
     */
    @Builder.Default
    private boolean encore = false;
}
