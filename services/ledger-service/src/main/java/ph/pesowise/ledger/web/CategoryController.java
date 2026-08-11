package ph.pesowise.ledger.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.ledger.api.LedgerDtos.CategoryRequest;
import ph.pesowise.ledger.api.LedgerDtos.CategoryResponse;
import ph.pesowise.ledger.service.CategoryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** Seeds the default set on a user's first call. */
    @GetMapping
    public List<CategoryResponse> list(@RequestHeader(Headers.USER_ID) UUID userId) {
        return categoryService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(
            @RequestHeader(Headers.USER_ID) UUID userId, @Valid @RequestBody CategoryRequest request) {
        return categoryService.create(userId, request);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(
            @RequestHeader(Headers.USER_ID) UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        categoryService.delete(userId, id);
    }
}
