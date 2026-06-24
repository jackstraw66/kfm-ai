package kfm.ai.dao;

import kfm.ai.types.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {

    /**
     * Finds all songs matching the given title (case-insensitive),
     * eagerly fetching the parent song_set and set_list for display.
     */
    @Query("""
            SELECT s FROM Song s
            JOIN FETCH s.songSet ss
            JOIN FETCH ss.setList sl
            WHERE LOWER(s.title) LIKE LOWER(CONCAT('%', :title, '%'))
            ORDER BY sl.date DESC
            """)
    List<Song> findByTitleContaining(@Param("title") String title);

    /**
     * Finds song pairs where the first song segues into the second song
     * (consecutive ordinals within the same set, first song has segue=true).
     * Returns the first song of each matching pair.
     */
    @Query("""
            SELECT s1 FROM Song s1
            JOIN Song s2 ON s2.songSet = s1.songSet AND s2.ordinal = s1.ordinal + 1
            JOIN FETCH s1.songSet ss
            JOIN FETCH ss.setList sl
            WHERE s1.segue = true
              AND LOWER(s1.title) LIKE LOWER(CONCAT('%', :fromTitle, '%'))
              AND LOWER(s2.title) LIKE LOWER(CONCAT('%', :toTitle, '%'))
            ORDER BY sl.date DESC
            """)
    List<Song> findSegues(@Param("fromTitle") String fromTitle, @Param("toTitle") String toTitle);

    /**
     * Returns distinct song titles for autocomplete/suggestion purposes.
     */
    @Query("SELECT DISTINCT s.title FROM Song s ORDER BY s.title")
    List<String> findDistinctTitles();
}
