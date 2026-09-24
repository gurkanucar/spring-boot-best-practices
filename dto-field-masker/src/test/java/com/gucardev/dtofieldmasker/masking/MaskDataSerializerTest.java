package com.gucardev.dtofieldmasker.masking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gucardev.dtofieldmasker.masking.MaskData.MaskingOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

/** The Jackson wiring, without Spring: annotation settings must reach the serializer per property. */
class MaskDataSerializerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    record Defaults(@MaskData String value) {
    }

    record TwoFields(
            @MaskData(maskingOption = MaskingOption.FIRST_X_CHARS_MASKED, value = 2) String first,
            @MaskData(replaceChar = "*", maskingOption = MaskingOption.LAST_X_CHARS_MASKED, value = 3) String second) {
    }

    record Nested(String name, List<Defaults> items) {
    }

    record WithNull(@MaskData String value, String other) {
    }

    record OnInteger(@MaskData Integer value) {
    }

    record Negative(@MaskData(value = -1) String value) {
    }

    static class PojoField {
        @MaskData(replaceChar = "#", value = 2)
        public String secret = "abcdef";
    }

    static class PojoGetter {
        @MaskData(value = 2)
        public String getSecret() {
            return "abcdef";
        }
    }

    @Test
    void usesAnnotationDefaults() throws Exception {
        assertThat(mapper.writeValueAsString(new Defaults("1234567890"))).isEqualTo("{\"value\":\"xxxxxxx890\"}");
    }

    @Test
    void eachPropertyGetsItsOwnSettings() throws Exception {
        assertThat(mapper.writeValueAsString(new TwoFields("123456", "123456")))
                .isEqualTo("{\"first\":\"xx3456\",\"second\":\"123***\"}");
    }

    @Test
    void masksInsideCollectionsAndNestedObjects() throws Exception {
        String json = mapper.writeValueAsString(new Nested("n", List.of(new Defaults("111111"), new Defaults("222222"))));

        assertThat(json).isEqualTo("{\"name\":\"n\",\"items\":[{\"value\":\"xxx111\"},{\"value\":\"xxx222\"}]}");
    }

    @Test
    void leavesNullsAlone() throws Exception {
        assertThat(mapper.writeValueAsString(new WithNull(null, "plain")))
                .isEqualTo("{\"value\":null,\"other\":\"plain\"}");
    }

    @Test
    void worksOnPojoFieldsAndGetters() throws Exception {
        assertThat(mapper.writeValueAsString(new PojoField())).isEqualTo("{\"secret\":\"####ef\"}");
        assertThat(mapper.writeValueAsString(new PojoGetter())).isEqualTo("{\"secret\":\"xxxxef\"}");
    }

    @Test
    void failsFastOnNonStringProperties() {
        assertThatThrownBy(() -> mapper.writeValueAsString(new OnInteger(1)))
                .isInstanceOf(DatabindException.class)
                .hasMessageContaining("@MaskData supports String properties only")
                .hasMessageContaining("'value'");
    }

    @Test
    void failsFastOnNegativeValue() {
        assertThatThrownBy(() -> mapper.writeValueAsString(new Negative("abc")))
                .isInstanceOf(DatabindException.class)
                .hasMessageContaining("must not be negative");
    }
}
