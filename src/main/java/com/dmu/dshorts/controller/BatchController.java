package com.dmu.dshorts.controller;

import com.dmu.dshorts.model.CardReport;
import com.dmu.dshorts.model.SharedCard;
import com.dmu.dshorts.repository.CardReportRepository;
import com.dmu.dshorts.repository.SharedCardRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api")
public class BatchController {
    private final SharedCardRepository cards;
    private final CardReportRepository reports;

    public BatchController(SharedCardRepository cards, CardReportRepository reports) {
        this.cards = cards;
        this.reports = reports;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "database", "postgresql", "ai", "not-configured");
    }

    @PostMapping("/batches")
    public Map<String, Object> batch(@RequestBody Map<String, Object> request) {
        Object topics = request.get("topics");
        if (!(topics instanceof List<?> topicList) || topicList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "관심사를 하나 이상 입력해주세요.");
        }

        List<SharedCard> found = cards.findTop3ByPublishedTrueAndHiddenFalseOrderByIdAsc();
        if (found.size() < 3) seed();

        String requestId = UUID.randomUUID().toString();
        List<SharedCard> result = cards.findTop3ByPublishedTrueAndHiddenFalseOrderByIdAsc();
        List<Map<String, Object>> output = result.stream().map(card -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", card.getId());
            item.put("topic", card.getTopic());
            item.put("title", card.getTitle());
            item.put("content", card.getContent());
            item.put("source", card.getSource());
            item.put("publisher", card.getSource());
            item.put("url", card.getSourceUrl());
            item.put("requestId", requestId);
            item.put("imageStatus", "PENDING");
            return item;
        }).toList();

        return Map.of(
            "requestId", requestId,
            "cards", output,
            "quiz", Map.of(
                "question", "꿀벌의 춤이 알려주는 것은 무엇일까요?",
                "options", List.of("벌집의 온도", "먹이의 방향과 거리", "날씨의 변화"),
                "answer", 1,
                "explanation", "춤의 방향과 반복 속도가 먹이의 방향과 대략적인 거리를 전달합니다."
            )
        );
    }

    @GetMapping("/batches/{requestId}/cards/{cardId}/image")
    public Map<String, Object> imageStatus(@PathVariable String requestId, @PathVariable Long cardId) {
        if (requestId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 ID가 필요합니다.");
        }
        cards.findById(cardId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "카드를 찾지 못했어요."));
        return Map.of(
            "cardId", cardId,
            "imageStatus", "FAILED",
            "message", "이미지 제공자가 아직 연결되지 않았습니다."
        );
    }

    @PostMapping("/cards/{id}/reports")
    public Map<String, String> report(@PathVariable Long id, @RequestBody Map<String, String> body) {
        SharedCard card = cards.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "카드를 찾지 못했어요."));
        reports.save(new CardReport(card, body.getOrDefault("reason", "기타")));
        return Map.of("status", "received", "cardId", id.toString());
    }

    private void seed() {
        cards.saveAll(List.of(
            new SharedCard("자연", "벌이 춤으로 길을 알려주는 이유", "꿀벌은 먹이가 있는 방향과 거리를 동료에게 알리기 위해 몸을 흔드는 춤을 춥니다.", "Encyclopaedia Britannica", "https://www.britannica.com/science/bee"),
            new SharedCard("환경", "도시의 나무가 기온을 낮추는 방식", "나무는 잎의 증산 작용과 그늘로 주변의 열을 줄입니다.", "NASA Earth Observatory", "https://earthobservatory.nasa.gov/"),
            new SharedCard("우주", "우주에서 소리는 왜 들리지 않을까?", "소리는 진동을 전달할 매질이 필요합니다. 우주는 대부분 진공입니다.", "NASA Space Place", "https://spaceplace.nasa.gov/sound-in-space/en/")
        ));
    }
}
