package com.dmu.dshorts.domain.topic.repository;

import com.dmu.dshorts.domain.topic.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TopicRepository extends JpaRepository<Topic,String> {}
