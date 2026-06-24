package kfm.ai.ingest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import kfm.ai.types.SetList;
import kfm.ai.types.Song;
import kfm.ai.types.SongSet;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client for the setlist.fm REST API.
 *
 * <p>Fetches all setlists for the Grateful Dead using the
 * {@code /1.0/artist/{mbid}/setlists} endpoint with pagination.</p>
 */
@Slf4j
@Component
public class SetlistFmApiClient {

    private static final String BASE_URL = "https://api.setlist.fm/rest";
    private static final String GRATEFUL_DEAD_MBID = "6faa7ca7-0d99-4a5e-bfa6-1fd5037520c6";
    private static final DateTimeFormatter EVENT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestClient restClient;
    private final BatchIngestProperties properties;

    public SetlistFmApiClient(RestClient.Builder restClientBuilder, BatchIngestProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-api-key", properties.getApiKey())
                .build();
    }

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 2000;

    /**
     * Fetches all Grateful Dead setlists from the API, page by page.
     * Applies rate-limit delay between page fetches and retries with
     * exponential backoff on 429 (Too Many Requests) responses.
     *
     * @return list of SetList domain objects mapped from the API response
     */
    public List<SetList> fetchAllSetlists() {
        List<SetList> allSetlists = new ArrayList<>();
        int page = 1;
        int totalPages = 1; // will be updated from first response

        while (page <= totalPages) {
            if (page > 1) {
                applyRateLimitDelay();
            }

            SetlistsResponse response = fetchPageWithRetry(page);

            if (response == null || response.setlist() == null) {
                log.warn("Empty or failed response for page {}, stopping pagination", page);
                break;
            }

            // Calculate total pages from first response
            if (page == 1 && response.total() > 0 && response.itemsPerPage() > 0) {
                totalPages = (int) Math.ceil((double) response.total() / response.itemsPerPage());
                log.info("Discovered {} total setlists across {} pages", response.total(), totalPages);
            }

            for (ApiSetlist apiSetlist : response.setlist()) {
                try {
                    SetList setList = mapToEntity(apiSetlist);
                    if (setList != null) {
                        allSetlists.add(setList);
                    }
                } catch (RuntimeException ex) {
                    log.warn("Failed to map setlist {}: {}", apiSetlist.id(), ex.getMessage());
                }
            }

            if (page % 10 == 0) {
                log.info("Fetched page {}/{}, {} setlists collected so far", page, totalPages, allSetlists.size());
            }

            page++;
        }

        return allSetlists;
    }

    /**
     * Fetches a single page with retry logic for 429 rate-limit responses.
     * Uses exponential backoff: 2s, 4s, 8s between retries.
     *
     * @param page the page number to fetch
     * @return the response, or null if all retries are exhausted
     */
    private SetlistsResponse fetchPageWithRetry(int page) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return restClient.get()
                        .uri("/1.0/artist/{mbid}/setlists?p={page}", GRATEFUL_DEAD_MBID, page)
                        .retrieve()
                        .body(SetlistsResponse.class);
            } catch (RestClientException ex) {
                String message = ex.getMessage();
                boolean isRateLimited = message != null && message.contains("429");

                if (isRateLimited && attempt < MAX_RETRIES) {
                    long backoff = RETRY_BACKOFF_MS * (1L << (attempt - 1)); // 2s, 4s, 8s
                    log.warn("Rate limited on page {} (attempt {}/{}), retrying in {}ms",
                            page, attempt, MAX_RETRIES, backoff);
                    sleep(backoff);
                } else {
                    log.error("Failed to fetch setlists page {} (attempt {}/{}): {}",
                            page, attempt, MAX_RETRIES, message);
                    return null;
                }
            }
        }
        return null;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Sleep interrupted during retry backoff");
        }
    }

    /**
     * Maps an API setlist response to our domain entity.
     */
    SetList mapToEntity(ApiSetlist apiSetlist) {
        if (apiSetlist.eventDate() == null || apiSetlist.eventDate().isBlank()) {
            return null;
        }

        LocalDateTime date;
        try {
            date = LocalDate.parse(apiSetlist.eventDate(), EVENT_DATE_FORMAT).atStartOfDay();
        } catch (Exception ex) {
            log.warn("Unparseable date '{}' for setlist {}", apiSetlist.eventDate(), apiSetlist.id());
            return null;
        }

        String sourceUrl = apiSetlist.url() != null ? apiSetlist.url() : 
                "https://www.setlist.fm/setlist/" + apiSetlist.id();

        List<SongSet> songSets = new ArrayList<>();
        if (apiSetlist.sets() != null && apiSetlist.sets().set() != null) {
            int ordinal = 1;
            for (ApiSet apiSet : apiSetlist.sets().set()) {
                SongSet songSet = mapSongSet(apiSet, ordinal++);
                songSets.add(songSet);
            }
        }

        // Venue mapping
        String venueName = null;
        String cityName = null;
        String state = null;

        if (apiSetlist.venue() != null) {
            String rawName = apiSetlist.venue().name();
            if (rawName != null && !rawName.isBlank()) {
                venueName = rawName.length() > 512 ? rawName.substring(0, 512) : rawName;
            }

            ApiCity apiCity = apiSetlist.venue().city();
            if (apiCity != null) {
                if (apiCity.name() != null && !apiCity.name().isBlank()) {
                    cityName = apiCity.name();
                }
                // Prefer stateCode; fall back to state name
                if (apiCity.stateCode() != null && !apiCity.stateCode().isBlank()) {
                    state = apiCity.stateCode();
                } else if (apiCity.state() != null && !apiCity.state().isBlank()) {
                    state = apiCity.state();
                }
            }
        }

        return SetList.builder()
                .date(date)
                .sourceUrl(sourceUrl)
                .venueName(venueName)
                .city(cityName)
                .state(state)
                .songSets(songSets)
                .build();
    }

    private SongSet mapSongSet(ApiSet apiSet, int ordinal) {
        boolean encore = apiSet.encore() != null && apiSet.encore() > 0;

        List<Song> songs = new ArrayList<>();
        if (apiSet.song() != null) {
            for (ApiSong apiSong : apiSet.song()) {
                String info = apiSong.info();
                boolean segue = false;

                // Detect segue marker (>) in the info field
                if (info != null) {
                    // Check if info contains a segue marker
                    if (info.contains(">")) {
                        segue = true;
                        // Remove common segue patterns: "(>", "(> ", ">", ") (>"
                        info = info.replaceAll("\\s*\\(\\s*>\\s*\\)?", "").trim();
                        info = info.replace(">", "").trim();
                        // Clean up trailing orphan parens
                        info = info.replaceAll("\\)\\s*$", "").trim();
                        info = info.replaceAll("^\\s*\\(", "").trim();
                    }
                    // If info is now empty or just whitespace, set to null
                    if (info.isBlank()) {
                        info = null;
                    }
                }

                Song song = Song.builder()
                        .title(apiSong.name() != null ? apiSong.name() : "Unknown")
                        .annotation(info)
                        .segue(segue)
                        .build();
                songs.add(song);
            }
        }

        return SongSet.builder()
                .ordinal(ordinal)
                .encore(encore)
                .songs(songs)
                .build();
    }

    private void applyRateLimitDelay() {
        try {
            Thread.sleep(properties.getRequestDelayMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Rate-limit delay interrupted");
        }
    }

    // ── API response records ─────────────────────────────────────────────

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record SetlistsResponse(
            int total,
            int page,
            int itemsPerPage,
            List<ApiSetlist> setlist
    ) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiSetlist(
            String id,
            String versionId,
            String eventDate,
            String url,
            String info,
            ApiArtist artist,
            ApiVenue venue,
            ApiSets sets
    ) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiSets(List<ApiSet> set) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiSet(
            String name,
            Integer encore,
            List<ApiSong> song
    ) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiSong(
            String name,
            String info,
            Boolean tape
    ) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiArtist(String mbid, String name) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiVenue(String id, String name, ApiCity city) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiCity(String name, String state, String stateCode) {}
}
