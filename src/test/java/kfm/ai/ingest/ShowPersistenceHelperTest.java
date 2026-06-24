package kfm.ai.ingest;

import kfm.ai.dao.SetListRepository;
import kfm.ai.types.SetList;
import kfm.ai.types.Song;
import kfm.ai.types.SongSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ShowPersistenceHelper}.
 */
@ExtendWith(MockitoExtension.class)
class ShowPersistenceHelperTest {

    @Mock
    private SetListRepository setListRepository;

    private ShowPersistenceHelper helper;

    @BeforeEach
    void setUp() {
        helper = new ShowPersistenceHelper(setListRepository);
    }

    @Test
    void persistShow_duplicateDate_returnsSkipped() {
        SetList setList = buildSetList("2024-05-10T20:00");
        when(setListRepository.existsByDate(setList.getDate())).thenReturn(true);

        PersistResult result = helper.persistShow(setList);

        assertEquals(PersistResult.SKIPPED, result);
        verify(setListRepository, never()).save(any());
    }

    @Test
    void persistShow_duplicateSourceUrl_returnsSkipped() {
        SetList setList = buildSetList("1977-05-08T20:00");
        when(setListRepository.existsByDate(any())).thenReturn(false);
        when(setListRepository.existsBySourceUrl(setList.getSourceUrl())).thenReturn(true);

        PersistResult result = helper.persistShow(setList);

        assertEquals(PersistResult.SKIPPED, result);
        verify(setListRepository, never()).save(any());
    }

    @Test
    void persistShow_saveSucceeds_returnsIngested() {
        SetList setList = buildSetList("1973-06-10T20:00");
        when(setListRepository.existsByDate(any())).thenReturn(false);
        when(setListRepository.existsBySourceUrl(any())).thenReturn(false);
        when(setListRepository.save(any())).thenReturn(setList);

        PersistResult result = helper.persistShow(setList);

        assertEquals(PersistResult.INGESTED, result);
        verify(setListRepository).save(setList);
    }

    @Test
    void persistShow_saveThrowsRuntimeException_returnsFailed() {
        SetList setList = buildSetList("1971-04-28T20:00");
        when(setListRepository.existsByDate(any())).thenReturn(false);
        when(setListRepository.existsBySourceUrl(any())).thenReturn(false);
        when(setListRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        PersistResult result = helper.persistShow(setList);

        assertEquals(PersistResult.FAILED, result);
    }

    @Test
    void persistShow_setsBackReferencesAndOrdinals() {
        Song song1 = Song.builder().title("Truckin'").build();
        Song song2 = Song.builder().title("Dark Star").build();
        SongSet songSet = SongSet.builder()
                .ordinal(1)
                .songs(new ArrayList<>(List.of(song1, song2)))
                .build();
        SetList setList = SetList.builder()
                .date(LocalDateTime.of(1977, 5, 8, 20, 0))
                .sourceUrl("http://example.com/1")
                .songSets(new ArrayList<>(List.of(songSet)))
                .build();

        when(setListRepository.existsByDate(any())).thenReturn(false);
        when(setListRepository.existsBySourceUrl(any())).thenReturn(false);
        when(setListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        helper.persistShow(setList);

        // Verify back-references were set
        assertSame(setList, songSet.getSetList());
        assertSame(songSet, song1.getSongSet());
        assertSame(songSet, song2.getSongSet());
        // Verify ordinals assigned
        assertEquals(1, song1.getOrdinal());
        assertEquals(2, song2.getOrdinal());
    }

    @Test
    void persistShow_nullSongSets_handledGracefully() {
        SetList setList = SetList.builder()
                .date(LocalDateTime.of(1970, 1, 1, 0, 0))
                .sourceUrl("http://example.com/null-sets")
                .songSets(null)
                .build();

        when(setListRepository.existsByDate(any())).thenReturn(false);
        when(setListRepository.existsBySourceUrl(any())).thenReturn(false);
        when(setListRepository.save(any())).thenReturn(setList);

        PersistResult result = helper.persistShow(setList);

        assertEquals(PersistResult.INGESTED, result);
    }

    @Test
    void persistShow_songSetWithNullSongsList_handledGracefully() {
        SongSet songSet = SongSet.builder().ordinal(1).songs(null).build();
        SetList setList = SetList.builder()
                .date(LocalDateTime.of(1970, 2, 1, 0, 0))
                .sourceUrl("http://example.com/null-songs")
                .songSets(new ArrayList<>(List.of(songSet)))
                .build();

        when(setListRepository.existsByDate(any())).thenReturn(false);
        when(setListRepository.existsBySourceUrl(any())).thenReturn(false);
        when(setListRepository.save(any())).thenReturn(setList);

        PersistResult result = helper.persistShow(setList);

        assertEquals(PersistResult.INGESTED, result);
        assertSame(setList, songSet.getSetList());
    }

    private SetList buildSetList(String dateTime) {
        return SetList.builder()
                .date(LocalDateTime.parse(dateTime))
                .sourceUrl("http://example.com/" + dateTime)
                .songSets(new ArrayList<>())
                .build();
    }
}
