package com.dmu.dshorts.domain.activity.repository;

import com.dmu.dshorts.domain.activity.entity.UserInterest;
import com.dmu.dshorts.domain.activity.entity.UserInterestId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserInterestRepository extends JpaRepository<UserInterest, UserInterestId> {

    List<UserInterest> findByUserId(Long userId);
}
