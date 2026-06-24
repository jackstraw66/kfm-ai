package kfm.ai.service;

import kfm.ai.dao.SongRepository;
import kfm.ai.types.Song;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ShowSearchService {

    private final SongRepository songRepository;

    public ShowSearchService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    /**
     * Searches for shows where a song matching the given title was performed.
     */
    public List<Song> searchBySong(String title) {
        return songRepository.findByTitleContaining(title.trim());
    }

    /**
     * Searches for shows where {@code fromTitle} segues into {@code toTitle}.
     */
    public List<Song> searchBySegue(String fromTitle, String toTitle) {
        return songRepository.findSegues(fromTitle.trim(), toTitle.trim());
    }

    /**
     * Returns all distinct song titles for suggestions.
     */
    public List<String> getAllSongTitles() {
        return songRepository.findDistinctTitles();
    }
}
