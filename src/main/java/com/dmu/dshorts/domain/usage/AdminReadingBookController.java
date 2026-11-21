package com.dmu.dshorts.domain.usage;

import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reading-books")
public class AdminReadingBookController {

    private final ReadingBookService books;

    public AdminReadingBookController(ReadingBookService books) {
        this.books = books;
    }

    @GetMapping
    public ApiResponse<PageResponse<ReadingBookService.Book>> list() {
        return ApiResponse.of(PageResponse.of(books.list(), null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReadingBookService.Book> create(@RequestBody ReadingBookService.BookInput input) {
        return ApiResponse.of(books.save(null, input));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ReadingBookService.Book> update(@PathVariable Long id, @RequestBody JsonNode input) {
        return ApiResponse.of(books.patch(id, input));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        books.delete(id);
    }
}
