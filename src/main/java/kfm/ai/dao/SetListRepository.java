package kfm.ai.dao;

import kfm.ai.types.SetList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface SetListRepository extends JpaRepository<SetList, Long> {

    boolean existsByDate(LocalDateTime date);

    boolean existsBySourceUrl(String sourceUrl);
}
