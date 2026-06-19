package kfm.ai.ingest;

import java.util.List;

import kfm.ai.dao.SetListRepository;
import kfm.ai.types.SetList;
import kfm.ai.types.Song;
import kfm.ai.types.SongSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the persistence of a single show within its own transaction.
 *
 * <p>Each call to {@link #persistShow(SetList)} runs in a new transaction
 * ({@code REQUIRES_NEW}) so that a failure on one show does not roll back others.</p>
 */
@Slf4j
@Component
public class ShowPersistenceHelper {

    private final SetListRepository setListRepository;

    public ShowPersistenceHelper(SetListRepository setListRepository) {
        this.setListRepository = setListRepository;
    }

    /**
     * Persists a single show in its own transaction.
     *
     * <p>Before persisting, this method:
     * <ul>
     *   <li>Sets bidirectional back-references (SongSet → SetList, Song → SongSet)</li>
     *   <li>Assigns 1-based song ordinals within each SongSet</li>
     *   <li>Checks for existing records by date and sourceUrl</li>
     * </ul>
     *
     * @param setList the SetList entity to persist (with sourceUrl already set)
     * @return the result of the persist attempt
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PersistResult persistShow(SetList setList) {
        // Dedup check: skip if date or sourceUrl already exists
        if (setListRepository.existsByDate(setList.getDate())) {
            log.info("Skipping already-ingested show: date={}, url={}", setList.getDate(), setList.getSourceUrl());
            return PersistResult.SKIPPED;
        }
        if (setListRepository.existsBySourceUrl(setList.getSourceUrl())) {
            log.info("Skipping already-ingested show: date={}, url={}", setList.getDate(), setList.getSourceUrl());
            return PersistResult.SKIPPED;
        }

        // Set back-references and ordinals before persist
        prepareEntityGraph(setList);

        // Persist
        try {
            setListRepository.save(setList);
            return PersistResult.INGESTED;
        } catch (RuntimeException ex) {
            log.error("Failed to persist show: {} - {}", setList.getSourceUrl(), ex.getMessage());
            return PersistResult.FAILED;
        }
    }

    /**
     * Sets bidirectional back-references and song ordinals on the entity graph
     * so that JPA cascade persist works correctly.
     */
    private void prepareEntityGraph(SetList setList) {
        List<SongSet> songSets = setList.getSongSets();
        if (songSets == null) {
            return;
        }
        for (SongSet songSet : songSets) {
            songSet.setSetList(setList);
            List<Song> songs = songSet.getSongs();
            if (songs != null) {
                int ordinal = 1;
                for (Song song : songs) {
                    song.setSongSet(songSet);
                    song.setOrdinal(ordinal++);
                }
            }
        }
    }
}
