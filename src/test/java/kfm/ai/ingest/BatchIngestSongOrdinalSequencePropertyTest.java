package kfm.ai.ingest;

import kfm.ai.dao.SetListRepository;
import kfm.ai.types.SetList;
import kfm.ai.types.Song;
import kfm.ai.types.SongSet;
import net.jqwik.api.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test for song ordinal sequence assignment during persistence.
 *
 * // Feature: setlist-batch-ingest, Property 4: Song Ordinal Sequence
 *
 * <p>Validates: Requirements 3.6</p>
 *
 * <p>For any SongSet containing N songs (where N ≥ 0), after ordinal assignment
 * the songs SHALL have ordinal values forming the contiguous sequence 1, 2, 3, …, N
 * with no gaps and no duplicates, preserving the original list order.</p>
 */
class BatchIngestSongOrdinalSequencePropertyTest {

    private final SetListRepository setListRepository = mock(SetListRepository.class);
    private final ShowPersistenceHelper helper = new ShowPersistenceHelper(setListRepository);

    /**
     * Property 4: For any list of 0–20 songs with initially unset ordinals (0),
     * after calling persistShow() the songs SHALL have ordinal values forming
     * the contiguous sequence [1, 2, 3, …, N] with no gaps and no duplicates,
     * preserving the original list order.
     *
     * <p>Validates: Requirements 3.6</p>
     */
    @Property(tries = 200)
    void songOrdinalsFormContiguousSequenceAfterPersist(
            @ForAll("songLists") List<String> songTitles
    ) {
        // Arrange: mock repository to allow persistence (no duplicates)
        when(setListRepository.existsByDate(any())).thenReturn(false);
        when(setListRepository.existsBySourceUrl(any())).thenReturn(false);
        when(setListRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int n = songTitles.size();

        // Build songs with ordinal=0 (unset)
        List<Song> songs = new ArrayList<>();
        for (String title : songTitles) {
            songs.add(Song.builder().title(title).ordinal(0).build());
        }

        // Build a SetList with a single SongSet containing the songs
        SongSet songSet = SongSet.builder()
                .ordinal(1)
                .songs(songs)
                .build();

        SetList setList = SetList.builder()
                .date(LocalDateTime.of(1977, 5, 8, 20, 0))
                .sourceUrl("https://www.setlist.fm/setlist/test-" + n)
                .songSets(new ArrayList<>(List.of(songSet)))
                .build();

        // Act: persist via helper (triggers prepareEntityGraph)
        helper.persistShow(setList);

        // Assert: ordinals form contiguous 1..N sequence
        List<Song> resultSongs = setList.getSongSets().get(0).getSongs();
        assertEquals(n, resultSongs.size(),
                "Song count should remain " + n + " after persistence");

        // Verify contiguous sequence [1, 2, ..., N]
        List<Integer> expectedOrdinals = IntStream.rangeClosed(1, n)
                .boxed()
                .toList();

        List<Integer> actualOrdinals = resultSongs.stream()
                .map(Song::getOrdinal)
                .toList();

        assertEquals(expectedOrdinals, actualOrdinals,
                "Ordinals must form contiguous sequence [1.." + n + "]. "
                        + "Actual: " + actualOrdinals);

        // Verify no duplicates
        Set<Integer> ordinalSet = new HashSet<>(actualOrdinals);
        assertEquals(n, ordinalSet.size(),
                "Ordinals must have no duplicates. Actual: " + actualOrdinals);

        // Verify order preservation: song at index i should have ordinal i+1
        for (int i = 0; i < n; i++) {
            assertEquals(i + 1, resultSongs.get(i).getOrdinal(),
                    "Song at index " + i + " should have ordinal " + (i + 1)
                            + " (title: " + resultSongs.get(i).getTitle() + ")");
        }
    }

    // ── Arbitraries ──────────────────────────────────────────────────────

    /**
     * Generates random song title lists of size 0..20.
     * Each title is a random alphabetic string of 3–30 characters.
     */
    @Provide
    Arbitrary<List<String>> songLists() {
        Arbitrary<String> titleArbitrary = Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(30);

        return titleArbitrary.list().ofMinSize(0).ofMaxSize(20);
    }
}
