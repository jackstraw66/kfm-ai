package kfm.ai.types;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "set_list", uniqueConstraints = {
    @UniqueConstraint(name = "uk_set_list_date", columnNames = "date"),
    @UniqueConstraint(name = "uk_set_list_source_url", columnNames = "source_url")
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SetList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime date;

    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;

    @OneToMany(mappedBy = "setList", cascade = {CascadeType.PERSIST, CascadeType.MERGE},
               orphanRemoval = true)
    @OrderBy("ordinal")
    @Builder.Default
    private List<SongSet> songSets = new ArrayList<>();
}
