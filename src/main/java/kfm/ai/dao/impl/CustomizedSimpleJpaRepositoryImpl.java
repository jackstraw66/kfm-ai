package kfm.ai.dao.impl;

import kfm.ai.dao.CustomizedSimpleJpaRepository;

import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.io.Serializable;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

public class CustomizedSimpleJpaRepositoryImpl<T, I extends Serializable>
        extends SimpleJpaRepository<T, I>
        implements CustomizedSimpleJpaRepository<T, I> {

    private final EntityManager entityManager;

    public CustomizedSimpleJpaRepositoryImpl(
            JpaEntityInformation<T, I> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }
    
    @Override
    @Transactional
    public void refresh(T t) {
        this.entityManager.refresh(t);
    }
}
