package com.dmu.dshorts.domain.activity.repository;

import com.dmu.dshorts.domain.activity.entity.SavedPost;
import com.dmu.dshorts.domain.activity.entity.SavedPostId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedPostRepository extends JpaRepository<SavedPost, SavedPostId> {
}
