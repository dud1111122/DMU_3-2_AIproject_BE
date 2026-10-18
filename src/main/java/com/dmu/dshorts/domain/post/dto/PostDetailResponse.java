package com.dmu.dshorts.domain.post.dto;

import java.util.List;
public record PostDetailResponse(PostSummaryResponse post,List<PostSectionResponse> sections,
    List<String> keyPoints,List<PostSourceResponse> sources) {}
