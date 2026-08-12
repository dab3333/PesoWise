package ph.pesowise.admin.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ph.pesowise.admin.domain.Feedback;

import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    @Query("SELECT f FROM Feedback f WHERE :status IS NULL OR f.status = :status")
    Page<Feedback> search(@Param("status") Feedback.Status status, Pageable pageable);

    long countByStatus(Feedback.Status status);
}
