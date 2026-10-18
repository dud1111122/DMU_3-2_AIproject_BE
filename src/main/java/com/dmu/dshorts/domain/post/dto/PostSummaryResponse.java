package com.dmu.dshorts.domain.post.dto;

import java.time.Instant;
import java.util.List;
public record PostSummaryResponse(String id,String title,String summary,String caption,String groundingMode,
    String personaId,String personaName,String personaHandle,List<String> topicIds,List<String> topicNames,
    long likeCount,boolean liked,boolean saved,Instant createdAt) {}
