package kfm.ai.types;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "song")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
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

    private int ordinal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_set_id", nullable = false)
    private SongSet songSet;
}
