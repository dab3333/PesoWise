package ph.pesowise.planning.repo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.data.repository.Repository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against a mistake that unit tests structurally cannot catch.
 *
 * <p>Spring Data only detects repository interfaces declared at the <em>top level</em>. Nesting them
 * inside a holder class compiles fine and every unit test still passes — because those tests
 * construct services directly with mocks and never start a Spring context — and then the application
 * fails at boot with "No qualifying bean of type BillRepository available". That happened once here,
 * and it was only caught by deploying.
 *
 * <p>This test costs a classpath scan and closes that gap without needing a database.
 */
class RepositoryDeclarationTest {

    @Test
    @DisplayName("every Spring Data repository is declared at the top level, so Spring can find it")
    void repositoriesAreTopLevel() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                // Repositories are interfaces, which the default filter rejects as non-concrete.
                return definition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));

        List<String> nested = scanner.findCandidateComponents("ph.pesowise.planning.repo").stream()
                .map(definition -> definition.getBeanClassName())
                .filter(name -> name != null && name.contains("$"))
                .toList();

        assertThat(nested)
                .as("nested repository interfaces are invisible to Spring Data and fail at startup")
                .isEmpty();
    }

    @Test
    @DisplayName("the repositories Spring needs to find are actually on the classpath")
    void repositoriesAreDiscoverable() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));

        List<String> found = scanner.findCandidateComponents("ph.pesowise.planning.repo").stream()
                .map(definition -> definition.getBeanClassName())
                .toList();

        // A sanity check on the scan itself: if this ever returns nothing, the test above would
        // pass vacuously and stop protecting anything.
        assertThat(found).isNotEmpty().anySatisfy(name ->
                assertThat(name).endsWith("RecurringBillRepository"));
    }
}
