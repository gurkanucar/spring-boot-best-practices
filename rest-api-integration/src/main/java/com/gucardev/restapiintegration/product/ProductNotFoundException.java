package com.gucardev.restapiintegration.product;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(long id) {
        super("Product " + id + " not found in the remote API");
    }
}
