package kfm.ai.controller;

import kfm.ai.service.ShowSearchService;
import kfm.ai.types.Song;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/shows")
public class ShowSearchController {

    private final ShowSearchService showSearchService;

    public ShowSearchController(ShowSearchService showSearchService) {
        this.showSearchService = showSearchService;
    }

    @GetMapping
    public String searchPage() {
        return "shows/search";
    }

    /** Handles song/segue search queries and populates the result model. */
    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) String song,
            @RequestParam(required = false) String fromSong,
            @RequestParam(required = false) String toSong,
            @RequestParam(defaultValue = "song") String searchType,
            Model model) {

        model.addAttribute("searchType", searchType);
        model.addAttribute("song", song);
        model.addAttribute("fromSong", fromSong);
        model.addAttribute("toSong", toSong);

        if ("segue".equals(searchType) && fromSong != null && toSong != null
                && !fromSong.isBlank() && !toSong.isBlank()) {
            List<Song> results = showSearchService.searchBySegue(fromSong, toSong);
            model.addAttribute("results", results);
            model.addAttribute("queryDescription",
                    fromSong.trim() + " > " + toSong.trim());
        } else if ("song".equals(searchType) && song != null && !song.isBlank()) {
            List<Song> results = showSearchService.searchBySong(song);
            model.addAttribute("results", results);
            model.addAttribute("queryDescription", song.trim());
        }

        return "shows/search";
    }

    /**
     * Returns distinct song titles as JSON for autocomplete.
     */
    @GetMapping("/titles")
    @ResponseBody
    public List<String> songTitles() {
        return showSearchService.getAllSongTitles();
    }
}
