package uk.gov.hscic.practitioner;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hscic.model.practitioner.PractitionerDetails;

@Service
public class PractitionerSearch {
    private final PractitionerEntityToObjectTransformer transformer = new PractitionerEntityToObjectTransformer();

    @Autowired
    private PractitionerRepository practitionerRepository;

    public PractitionerDetails findPractitionerDetails(final String practitionerId) {
        try {
            PractitionerEntity item = practitionerRepository.findById(Long.parseLong(practitionerId)).get();

            return item == null
                ? null
                : transformer.transform(item);
        } catch (NumberFormatException numberFormatException) {
            return null;
        }
    }

    public List<PractitionerDetails> findPractitionerByUserId(final String practitionerUserId) {
        return practitionerRepository.findByUserId(practitionerUserId)
                .stream()
                .map(transformer::transform)
                .collect(Collectors.toList());
    }
}
