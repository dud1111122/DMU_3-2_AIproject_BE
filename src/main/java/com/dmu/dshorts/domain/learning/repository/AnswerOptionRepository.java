package com.dmu.dshorts.domain.learning.repository;

import com.dmu.dshorts.domain.learning.entity.AnswerOption;
import com.dmu.dshorts.domain.learning.entity.AnswerOptionId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface AnswerOptionRepository extends JpaRepository<AnswerOption, AnswerOptionId> {

    List<AnswerOption> findByQuestionIdInOrderByQuestionIdAscOptionOrderAsc(Collection<Long> questionIds);
}
