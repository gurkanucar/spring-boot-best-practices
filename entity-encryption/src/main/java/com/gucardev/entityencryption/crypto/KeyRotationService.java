package com.gucardev.entityencryption.crypto;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-encrypts every value that is not yet written with the active key.
 *
 * <p>It works on the raw column values with SQL on purpose. Going through the entity would
 * decrypt and re-encrypt in memory, but Hibernate compares the decrypted attribute with its
 * snapshot, sees no change and would not issue an UPDATE.
 */
@Service
public class KeyRotationService {

    /** table -> encrypted columns. Constants from code, never user input, so safe to concatenate. */
    private static final Map<String, List<String>> ENCRYPTED_COLUMNS = Map.of(
            "customer", List.of("email", "national_id", "phone"));

    private final JdbcClient jdbc;
    private final AesGcmCipher cipher;

    public KeyRotationService(JdbcClient jdbc, AesGcmCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    /** @return the number of values that were re-encrypted */
    @Transactional
    public int rotate() {
        int rotated = 0;
        for (var table : ENCRYPTED_COLUMNS.entrySet()) {
            for (String column : table.getValue()) {
                var rows = jdbc.sql("select id, " + column + " as val from " + table.getKey()
                                + " where " + column + " is not null and " + column + " not like :prefix")
                        .param("prefix", cipher.activePrefix() + "%")
                        .query((rs, i) -> Map.entry(rs.getLong("id"), rs.getString("val")))
                        .list();
                for (var row : rows) {
                    jdbc.sql("update " + table.getKey() + " set " + column + " = :value where id = :id")
                            .param("value", cipher.encrypt(cipher.decrypt(row.getValue())))
                            .param("id", row.getKey())
                            .update();
                    rotated++;
                }
            }
        }
        return rotated;
    }
}
