package uk.gov.hscic.location;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hscic.model.location.LocationDetails;

@Service
public class LocationSearch {
    private final LocationEntityToLocationDetailsTransformer transformer = new LocationEntityToLocationDetailsTransformer();

    @Autowired
    private LocationRepository locationRepository;
    
    public List<LocationDetails> findAllLocations()
    {
        return locationRepository.findAll().stream().map(transformer::transform).collect(Collectors.toList());
    }

    public LocationDetails findLocationById(final String locationId) {

        final LocationEntity item;
        try {
            item = locationRepository.findById(Long.parseLong(locationId)).get();
        } catch (NumberFormatException e) {
            return null;
        }

        return item == null ? null : transformer.transform(item);
    }
}
