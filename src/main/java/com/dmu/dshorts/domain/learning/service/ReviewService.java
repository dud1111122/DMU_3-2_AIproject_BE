package com.dmu.dshorts.domain.learning.service;

import com.dmu.dshorts.domain.learning.repository.ReviewItemRepository;
import com.dmu.dshorts.domain.post.service.CatalogService;
import com.dmu.dshorts.global.common.PageResponse;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {
    private final ReviewItemRepository reviews;private final CatalogService catalog;
    public ReviewService(ReviewItemRepository reviews,CatalogService catalog){this.reviews=reviews;this.catalog=catalog;}
    @Transactional(readOnly=true) public PageResponse<Map<String,Object>> list(String user,String status,String cursor,int limit){var all=reviews.findByUserIdOrderByCreatedAtDesc(user).stream().filter(r->status==null||status.equalsIgnoreCase(r.getStatus())).<Map<String,Object>>map(r->{var detail=catalog.post(r.getPostId(),user);var m=new LinkedHashMap<String,Object>();m.put("id",r.getId());m.put("postId",r.getPostId());m.put("postTitle",detail.post().title());m.put("keyPointOrder",r.getKeyPointOrder());m.put("keyPoint",detail.keyPoints().get(r.getKeyPointOrder()));m.put("reason",r.getReason());m.put("status",r.getStatus());m.put("createdAt",r.getCreatedAt());return m;}).toList();return CatalogService.pageResponse(all,cursor,limit);}
    @Transactional public Map<String,Object> complete(String user,Long id){var item=reviews.findById(id).filter(r->r.getUserId().equals(user)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"복습 항목을 찾을 수 없습니다."));item.complete();return Map.of("id",id,"status",item.getStatus());}
}
