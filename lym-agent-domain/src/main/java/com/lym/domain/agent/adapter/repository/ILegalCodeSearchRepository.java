package com.lym.domain.agent.adapter.repository;

import com.lym.domain.agent.model.entity.LegalCodeSearchCommandEntity;
import com.lym.domain.agent.model.entity.LegalCodeSearchResultEntity;

import java.util.List;

public interface ILegalCodeSearchRepository {

    List<LegalCodeSearchResultEntity> searchLegalCode(LegalCodeSearchCommandEntity command);

}