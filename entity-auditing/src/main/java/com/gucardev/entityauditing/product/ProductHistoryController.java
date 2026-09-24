package com.gucardev.entityauditing.product;

import com.gucardev.entityauditing.product.dto.DeletedProduct;
import com.gucardev.entityauditing.product.dto.PriceChange;
import com.gucardev.entityauditing.product.dto.ProductResponse;
import com.gucardev.entityauditing.product.dto.ProductRevisionResponse;
import com.gucardev.entityauditing.product.dto.ProductSnapshot;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductHistoryController {

    private final ProductHistoryService historyService;
    private final ProductService productService;

    public ProductHistoryController(ProductHistoryService historyService, ProductService productService) {
        this.historyService = historyService;
        this.productService = productService;
    }

    /** Products that were deleted: who, when, and what they looked like. */
    @GetMapping("/deleted")
    public List<DeletedProduct> deleted() {
        return historyService.deleted();
    }

    @GetMapping("/{id}/history")
    public List<ProductRevisionResponse> history(@PathVariable Long id) {
        return historyService.history(id);
    }

    @GetMapping("/{id}/revisions/{revision}")
    public ProductRevisionResponse revision(@PathVariable Long id, @PathVariable Long revision) {
        return historyService.revision(id, revision);
    }

    @GetMapping("/{id}/price-history")
    public List<PriceChange> priceHistory(@PathVariable Long id) {
        return historyService.priceHistory(id);
    }

    /** {@code ?at=2026-09-24T10:15:00Z} */
    @GetMapping("/{id}/as-of")
    public ProductSnapshot asOf(@PathVariable Long id, @RequestParam Instant at) {
        return historyService.asOf(id, at);
    }

    /** Restores the values of an old revision. This is a new change, so it creates a new revision. */
    @PostMapping("/{id}/revert/{revision}")
    public ProductResponse revert(@PathVariable Long id, @PathVariable Long revision) {
        return productService.revert(id, revision);
    }
}
