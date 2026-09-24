package com.gucardev.restapiintegration.product;

import com.gucardev.restapiintegration.product.dto.ProductDto;
import com.gucardev.restapiintegration.product.dto.ProductPatchRequest;
import com.gucardev.restapiintegration.product.dto.ProductRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

/**
 * The same API as {@link ProductClient}, declared as an interface (Spring "HTTP interface",
 * similar to Feign). Spring generates the implementation on top of the configured RestClient,
 * so auth, headers, timeouts and error mapping are identical. See {@link ProductHttpApiConfig}.
 */
@HttpExchange(url = "/products", accept = "application/json")
public interface ProductHttpApi {

    @GetExchange
    List<ProductDto> list(@RequestParam(required = false) String name);

    @GetExchange("/{id}")
    ProductDto get(@PathVariable long id);

    @PostExchange(contentType = "application/json")
    ResponseEntity<ProductDto> create(@RequestBody ProductRequest request,
                                      @RequestHeader("Idempotency-Key") String idempotencyKey);

    @PutExchange(url = "/{id}", contentType = "application/json")
    ProductDto replace(@PathVariable long id, @RequestBody ProductRequest request);

    @PatchExchange(url = "/{id}", contentType = "application/json")
    ProductDto patch(@PathVariable long id, @RequestBody ProductPatchRequest request);

    @DeleteExchange("/{id}")
    void delete(@PathVariable long id);
}
