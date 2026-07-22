package kfm.ai.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;

import jakarta.persistence.EntityManager;

/**
 * Interface for JPA repositories requiring a {@code refresh()} method.
 *
 * @param <T> The type of entity being managed.
 * @param <I> The type of the ID of the managed entity.
 */
@NoRepositoryBean
public interface CustomizedSimpleJpaRepository<T, I extends Serializable> extends CrudRepository<T, I> {
    
    /**
     * Calls {@link EntityManager#refresh(Object)}.
     *
     * @param t The entity to refresh.
     */
    void refresh(T t);
}
