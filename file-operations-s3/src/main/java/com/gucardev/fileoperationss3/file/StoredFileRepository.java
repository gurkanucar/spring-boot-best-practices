package com.gucardev.fileoperationss3.file;

import com.gucardev.fileoperationss3.file.StoredFile.Status;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Page<StoredFile> findByStatus(Status status, Pageable pageable);
}
