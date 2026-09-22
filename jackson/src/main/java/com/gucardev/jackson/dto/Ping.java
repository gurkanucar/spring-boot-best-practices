package com.gucardev.jackson.dto;

// No getters, no fields - a bean Jackson considers to have zero properties. Without
// spring.jackson.serialization.fail-on-empty-beans=false, serializing this throws
// InvalidDefinitionException instead of writing "{}".
public class Ping {}
