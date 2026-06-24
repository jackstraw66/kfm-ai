package kfm.ai.ingest;

import kfm.ai.dao.SetListRepository;
import kfm.ai.ingest.SetlistFmApiClient.ApiCity;
import kfm.ai.ingest.SetlistFmApiClient.ApiSetlist;
import kfm.ai.ingest.SetlistFmApiClient.ApiSets;
import kfm.ai.ingest.SetlistFmApiClient.ApiVenue;
import kfm.ai.types.SetList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for venue data capture end-to-end.
 * Uses Testcontainers MySQL to validate persistence of venue fields.
 *
 * Validates: Requirements 1.5, 2.5, 4.1, 4.2, 4.3, 4.4, 4.5, 5.2, 5.3, 5.4
 */
@SpringBootTest(properties = {
        "spring.ai.ollama.chat.enabled=false",
        "spring.ai.ollama.init.pull-model-strategy=never"
})
@Import(MySqlTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "batch.ingest.request-delay-ms=100",
        "batch.ingest.index-url=http://localhost",
        "batch.ingest.api-key=test-key"
})
class VenueDataCaptureIntegrationTest {

    @Autowired
    private SetListRepository setListRepository;

    @Autowired
    private ShowPersistenceHelper showPersistenceHelper;

    @Autowired
    private BatchIngestProperties batchIngestProperties;

    private SetlistFmApiClient apiClient;

    @BeforeEach
    void setUp() {
        setListRepository.deleteAll();
        setListRepository.flush();
        apiClient = new SetlistFmApiClient(RestClient.builder(), batchIngestProperties);
    }

    @Test
    void persistEntityWithVenueFields_preservesAllValues() {
        // Persist a SetList with venue fields populated
        SetList setList = SetList.builder()
                .date(LocalDateTime.of(1977, 5, 8, 20, 0))
                .sourceUrl("https://www.setlist.fm/setlist/grateful-dead/1977/barton-hall-ithaca-ny-abc123.html")
                .venueName("Barton Hall, Cornell University")
                .city("Ithaca")
                .state("NY")
                .songSets(new ArrayList<>())
                .build();

        PersistResult result = showPersistenceHelper.persistShow(setList);
        assertThat(result).isEqualTo(PersistResult.INGESTED);

        // Reload and verify venue fields
        List<SetList> all = setListRepository.findAll();
        assertThat(all).hasSize(1);

        SetList loaded = all.get(0);
        assertThat(loaded.getVenueName()).isEqualTo("Barton Hall, Cornell University");
        assertThat(loaded.getCity()).isEqualTo("Ithaca");
        assertThat(loaded.getState()).isEqualTo("NY");
    }

    @Test
    void persistEntityWithNullVenueFields_loadsWithoutError() {
        // Persist a SetList with null venue fields (backward compatibility)
        SetList setList = SetList.builder()
                .date(LocalDateTime.of(1972, 8, 27, 20, 0))
                .sourceUrl("https://www.setlist.fm/setlist/grateful-dead/1972/veneta-or-xyz789.html")
                .venueName(null)
                .city(null)
                .state(null)
                .songSets(new ArrayList<>())
                .build();

        PersistResult result = showPersistenceHelper.persistShow(setList);
        assertThat(result).isEqualTo(PersistResult.INGESTED);

        // Reload and verify null venue fields load fine
        List<SetList> all = setListRepository.findAll();
        assertThat(all).hasSize(1);

        SetList loaded = all.get(0);
        assertThat(loaded.getVenueName()).isNull();
        assertThat(loaded.getCity()).isNull();
        assertThat(loaded.getState()).isNull();
        // Existing fields remain intact
        assertThat(loaded.getDate()).isEqualTo(LocalDateTime.of(1972, 8, 27, 20, 0));
        assertThat(loaded.getSourceUrl()).isEqualTo("https://www.setlist.fm/setlist/grateful-dead/1972/veneta-or-xyz789.html");
    }

    @Test
    void mapToEntityWithVenueData_thenPersist_venueFieldsCorrect() {
        // Create an ApiSetlist with full venue data
        ApiCity apiCity = new ApiCity("Morrison", "Colorado", "CO");
        ApiVenue apiVenue = new ApiVenue("venue-1", "Red Rocks Amphitheatre", apiCity);
        ApiSetlist apiSetlist = new ApiSetlist(
                "set-001",
                "v1",
                "07-08-1978",
                "https://www.setlist.fm/setlist/grateful-dead/1978/red-rocks-set001.html",
                null,
                null,
                apiVenue,
                new ApiSets(List.of())
        );

        // Map to entity
        SetList entity = apiClient.mapToEntity(apiSetlist);
        assertThat(entity).isNotNull();
        assertThat(entity.getVenueName()).isEqualTo("Red Rocks Amphitheatre");
        assertThat(entity.getCity()).isEqualTo("Morrison");
        assertThat(entity.getState()).isEqualTo("CO");

        // Persist and reload
        PersistResult result = showPersistenceHelper.persistShow(entity);
        assertThat(result).isEqualTo(PersistResult.INGESTED);

        List<SetList> all = setListRepository.findAll();
        assertThat(all).hasSize(1);

        SetList loaded = all.get(0);
        assertThat(loaded.getVenueName()).isEqualTo("Red Rocks Amphitheatre");
        assertThat(loaded.getCity()).isEqualTo("Morrison");
        assertThat(loaded.getState()).isEqualTo("CO");
    }

    @Test
    void mapToEntityWithNoVenueData_thenPersist_venueFieldsNull() {
        // Create an ApiSetlist with no venue data
        ApiSetlist apiSetlist = new ApiSetlist(
                "set-002",
                "v1",
                "01-01-1970",
                "https://www.setlist.fm/setlist/grateful-dead/1970/unknown-set002.html",
                null,
                null,
                null,  // no venue
                new ApiSets(List.of())
        );

        // Map to entity
        SetList entity = apiClient.mapToEntity(apiSetlist);
        assertThat(entity).isNotNull();
        assertThat(entity.getVenueName()).isNull();
        assertThat(entity.getCity()).isNull();
        assertThat(entity.getState()).isNull();

        // Persist and reload
        PersistResult result = showPersistenceHelper.persistShow(entity);
        assertThat(result).isEqualTo(PersistResult.INGESTED);

        List<SetList> all = setListRepository.findAll();
        assertThat(all).hasSize(1);

        SetList loaded = all.get(0);
        assertThat(loaded.getVenueName()).isNull();
        assertThat(loaded.getCity()).isNull();
        assertThat(loaded.getState()).isNull();
    }

    @Test
    void mapToEntityWithStateCodeFallback_thenPersist_stateUsesStateName() {
        // Create an ApiSetlist with stateCode absent but state name present
        ApiCity apiCity = new ApiCity("London", "England", null);
        ApiVenue apiVenue = new ApiVenue("venue-2", "Lyceum Theatre", apiCity);
        ApiSetlist apiSetlist = new ApiSetlist(
                "set-003",
                "v1",
                "26-05-1972",
                "https://www.setlist.fm/setlist/grateful-dead/1972/lyceum-set003.html",
                null,
                null,
                apiVenue,
                new ApiSets(List.of())
        );

        // Map to entity - should fall back to state name
        SetList entity = apiClient.mapToEntity(apiSetlist);
        assertThat(entity).isNotNull();
        assertThat(entity.getVenueName()).isEqualTo("Lyceum Theatre");
        assertThat(entity.getCity()).isEqualTo("London");
        assertThat(entity.getState()).isEqualTo("England");

        // Persist and reload
        PersistResult result = showPersistenceHelper.persistShow(entity);
        assertThat(result).isEqualTo(PersistResult.INGESTED);

        List<SetList> all = setListRepository.findAll();
        assertThat(all).hasSize(1);

        SetList loaded = all.get(0);
        assertThat(loaded.getState()).isEqualTo("England");
    }

    @Test
    void existingRowWithNullVenueFields_survivesContextReload() {
        // This tests that the schema supports existing data with null venue fields
        // (simulates post-migration behavior where old rows have null venue columns)
        SetList existing = SetList.builder()
                .date(LocalDateTime.of(1965, 12, 4, 20, 0))
                .sourceUrl("https://www.setlist.fm/setlist/grateful-dead/1965/first-show-def456.html")
                .venueName(null)
                .city(null)
                .state(null)
                .songSets(new ArrayList<>())
                .build();

        showPersistenceHelper.persistShow(existing);

        // Now persist a new row with venue data alongside the existing null-venue row
        SetList newShow = SetList.builder()
                .date(LocalDateTime.of(1995, 7, 9, 20, 0))
                .sourceUrl("https://www.setlist.fm/setlist/grateful-dead/1995/soldier-field-ghi012.html")
                .venueName("Soldier Field")
                .city("Chicago")
                .state("IL")
                .songSets(new ArrayList<>())
                .build();

        showPersistenceHelper.persistShow(newShow);

        // Verify both rows exist and are correctly loaded
        List<SetList> all = setListRepository.findAll();
        assertThat(all).hasSize(2);

        SetList oldRow = all.stream()
                .filter(s -> s.getDate().getYear() == 1965)
                .findFirst().orElseThrow();
        assertThat(oldRow.getVenueName()).isNull();
        assertThat(oldRow.getCity()).isNull();
        assertThat(oldRow.getState()).isNull();

        SetList newRow = all.stream()
                .filter(s -> s.getDate().getYear() == 1995)
                .findFirst().orElseThrow();
        assertThat(newRow.getVenueName()).isEqualTo("Soldier Field");
        assertThat(newRow.getCity()).isEqualTo("Chicago");
        assertThat(newRow.getState()).isEqualTo("IL");
    }
}
