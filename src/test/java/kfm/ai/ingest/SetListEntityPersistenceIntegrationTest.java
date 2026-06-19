package kfm.ai.ingest;

import kfm.ai.dao.SetListRepository;
import kfm.ai.types.SetList;
import kfm.ai.types.Song;
import kfm.ai.types.SongSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for JPA entity persistence of SetList, SongSet, and Song.
 * Uses Testcontainers MySQL for a real database environment.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 7.3
 */
@SpringBootTest(properties = {
        "spring.ai.ollama.chat.enabled=false",
        "spring.ai.ollama.init.pull-model-strategy=never"
})
@Import(MySqlTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=false",
        "batch.ingest.request-delay-ms=100",
        "batch.ingest.index-url=http://localhost"
})
class SetListEntityPersistenceIntegrationTest {

    @Autowired
    private SetListRepository setListRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        setListRepository.deleteAll();
        setListRepository.flush();
    }

    @Test
    @Transactional
    void cascadePersistCreateAllRows() {
        // Build a SetList with 2 SongSets, each with 3 Songs
        SetList setList = SetList.builder()
                .date(LocalDateTime.of(1977, 5, 8, 20, 0))
                .sourceUrl("https://www.setlist.fm/setlist/grateful-dead/1977/barton-hall-cornell-university-ithaca-ny-1bd6a04c.html")
                .songSets(new ArrayList<>())
                .build();

        for (int setIdx = 1; setIdx <= 2; setIdx++) {
            SongSet songSet = SongSet.builder()
                    .ordinal(setIdx)
                    .encore(false)
                    .setList(setList)
                    .songs(new ArrayList<>())
                    .build();

            for (int songIdx = 1; songIdx <= 3; songIdx++) {
                Song song = Song.builder()
                        .title("Song " + setIdx + "-" + songIdx)
                        .ordinal(songIdx)
                        .segue(false)
                        .songSet(songSet)
                        .build();
                songSet.getSongs().add(song);
            }

            setList.getSongSets().add(songSet);
        }

        // Persist
        SetList saved = setListRepository.saveAndFlush(setList);

        // Verify SetList has an ID
        assertThat(saved.getId()).isNotNull();

        // Verify all SongSets have IDs
        assertThat(saved.getSongSets()).hasSize(2);
        saved.getSongSets().forEach(ss -> {
            assertThat(ss.getId()).isNotNull();
            // Verify all Songs have IDs
            assertThat(ss.getSongs()).hasSize(3);
            ss.getSongs().forEach(song -> assertThat(song.getId()).isNotNull());
        });

        // Verify total row counts via queries
        Long setListCount = (Long) entityManager
                .createQuery("SELECT COUNT(sl) FROM SetList sl").getSingleResult();
        Long songSetCount = (Long) entityManager
                .createQuery("SELECT COUNT(ss) FROM SongSet ss").getSingleResult();
        Long songCount = (Long) entityManager
                .createQuery("SELECT COUNT(s) FROM Song s").getSingleResult();

        assertThat(setListCount).isEqualTo(1);
        assertThat(songSetCount).isEqualTo(2);
        assertThat(songCount).isEqualTo(6);
    }

    @Test
    @Transactional
    void orphanRemoval() {
        // Build a SetList with 2 SongSets
        SetList setList = SetList.builder()
                .date(LocalDateTime.of(1972, 8, 27, 20, 0))
                .sourceUrl("https://www.setlist.fm/setlist/grateful-dead/1972/veneta-or-abc123.html")
                .songSets(new ArrayList<>())
                .build();

        SongSet set1 = SongSet.builder()
                .ordinal(1)
                .encore(false)
                .setList(setList)
                .songs(new ArrayList<>())
                .build();
        set1.getSongs().add(Song.builder().title("Dark Star").ordinal(1).segue(false).songSet(set1).build());

        SongSet set2 = SongSet.builder()
                .ordinal(2)
                .encore(false)
                .setList(setList)
                .songs(new ArrayList<>())
                .build();
        set2.getSongs().add(Song.builder().title("Playing in the Band").ordinal(1).segue(false).songSet(set2).build());

        setList.getSongSets().add(set1);
        setList.getSongSets().add(set2);

        setListRepository.saveAndFlush(setList);

        // Verify initial state
        assertThat(setList.getSongSets()).hasSize(2);

        // Remove one SongSet from the list
        setList.getSongSets().remove(0);

        // Save again — orphan removal should delete the removed SongSet and its Songs
        setListRepository.saveAndFlush(setList);
        entityManager.flush();
        entityManager.clear();

        // Verify orphan removal: only 1 SongSet remains
        Long songSetCount = (Long) entityManager
                .createQuery("SELECT COUNT(ss) FROM SongSet ss").getSingleResult();
        assertThat(songSetCount).isEqualTo(1);

        // Verify the removed SongSet's songs are also deleted
        Long songCount = (Long) entityManager
                .createQuery("SELECT COUNT(s) FROM Song s").getSingleResult();
        assertThat(songCount).isEqualTo(1);
    }

    @Test
    void uniqueConstraintOnDate() {
        // Save first SetList
        SetList first = SetList.builder()
                .date(LocalDateTime.of(1987, 7, 4, 20, 0))
                .sourceUrl("https://www.setlist.fm/setlist/grateful-dead/1987/first-url.html")
                .songSets(new ArrayList<>())
                .build();
        setListRepository.saveAndFlush(first);

        // Try to save another with the same date but different sourceUrl
        SetList duplicate = SetList.builder()
                .date(LocalDateTime.of(1987, 7, 4, 20, 0))
                .sourceUrl("https://www.setlist.fm/setlist/grateful-dead/1987/different-url.html")
                .songSets(new ArrayList<>())
                .build();

        assertThatThrownBy(() -> setListRepository.saveAndFlush(duplicate))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraintOnSourceUrl() {
        // Save first SetList
        SetList first = SetList.builder()
                .date(LocalDateTime.of(1989, 10, 9, 20, 0))
                .sourceUrl("https://www.setlist.fm/setlist/grateful-dead/1989/shared-url.html")
                .songSets(new ArrayList<>())
                .build();
        setListRepository.saveAndFlush(first);

        // Try to save another with the same sourceUrl but different date
        SetList duplicate = SetList.builder()
                .date(LocalDateTime.of(1990, 3, 15, 20, 0))
                .sourceUrl("https://www.setlist.fm/setlist/grateful-dead/1989/shared-url.html")
                .songSets(new ArrayList<>())
                .build();

        assertThatThrownBy(() -> setListRepository.saveAndFlush(duplicate))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
