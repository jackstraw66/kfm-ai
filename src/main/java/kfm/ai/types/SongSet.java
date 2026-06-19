package kfm.ai.types;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "song_set")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SongSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The set number.  e.g.  Set I or Set II, etc.
     */
    private int ordinal;

    /**
     * True when this set block was labeled as an encore (label matches ^Encore.* case-insensitively).
     * All sets (regular and encore) appear in the same songSets list in DOM order.
     */
    @Builder.Default
    private boolean encore = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_list_id", nullable = false)
    private SetList setList;

    @OneToMany(mappedBy = "songSet", cascade = {CascadeType.PERSIST, CascadeType.MERGE},
               orphanRemoval = true)
    @OrderBy("ordinal")
    @Builder.Default
    private List<Song> songs = new ArrayList<>();
}
