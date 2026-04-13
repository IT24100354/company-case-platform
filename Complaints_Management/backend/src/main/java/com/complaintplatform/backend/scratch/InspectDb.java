
import com.complaintplatform.backend.model.Complaint;
import com.complaintplatform.backend.repository.ComplaintRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class InspectDb implements CommandLineRunner {
    private final ComplaintRepository repo;
    public InspectDb(ComplaintRepository repo) { this.repo = repo; }
    @Override
    public void run(String... args) {
        repo.findById(11L).ifPresentOrElse(
            c -> System.out.println("DEBUG: Complaint 11 found. ComplainantId: " + c.getComplainantId() + ", Title: " + c.getTitle()),
            () -> System.out.println("DEBUG: Complaint 11 NOT found.")
        );
    }
}
