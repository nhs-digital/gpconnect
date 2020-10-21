package uk.gov.hscic.organization;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hscic.model.organization.OrganizationDetails;

@Service
public class OrganizationSearch {
    private final OrganizationEntityToObjectTransformer transformer = new OrganizationEntityToObjectTransformer();

    @Autowired
    private OrganizationRepository organizationRepository;

    public OrganizationDetails findOrganizationDetails(final Long organizationId) {
        final OrganizationEntity item = organizationRepository.findById(organizationId).get();

        return item == null
                ? null
                : transformer.transform(item);
    }

    public List<OrganizationDetails> findOrganizationDetailsByOrgODSCode(String organizationODSCode) {
        return organizationRepository.findByOrgCode(organizationODSCode)
                .stream()
                .map(transformer::transform)
                .collect(Collectors.toList());
    }
}
