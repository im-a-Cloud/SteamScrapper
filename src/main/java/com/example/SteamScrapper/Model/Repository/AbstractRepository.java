package com.example.SteamScrapper.Model.Repository;

import com.example.SteamScrapper.Model.Entities.AbstractModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface AbstractRepository<M extends AbstractModel> extends JpaRepository<M, Long> {
}