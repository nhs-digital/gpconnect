package uk.gov.hscic.appointment.slot;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hscic.model.appointment.SlotDetail;

@Service
public class SlotSearch {

    private final SlotEntityToSlotDetailTransformer transformer = new SlotEntityToSlotDetailTransformer();

    @Autowired
    private SlotRepository slotRepository;

    /**
     *
     * @param id Slot Id
     * @return SlotDetail
     */
    public SlotDetail findSlotByID(Long id) {
        final SlotEntity item = slotRepository.findById(id).get();

        return item == null
                ? null
                : transformer.transform(item);
    }

    /**
     *
     * @param scheduleId
     * @param startDate
     * @param endDate
     * @param orgId
     * @return SlotDetail
     */
    public List<SlotDetail> findSlotsForScheduleIdAndOrganizationId(Long scheduleId, Date startDate, Date endDate, Long orgId) {
        return slotRepository.findByScheduleReferenceAndEndDateTimeAfterAndStartDateTimeBeforeAndGpConnectBookableTrueAndBookableOrganizationsId(scheduleId, startDate, endDate, orgId)
                .stream()
                .filter(slotEntity -> startDate == null || !slotEntity.getStartDateTime().before(startDate))
                .filter(slotEntity -> endDate == null || !slotEntity.getStartDateTime().after(endDate))
                .map(transformer::transform)
                .collect(Collectors.toList());
    }

    /**
     *
     * @param scheduleId
     * @param startDate
     * @param endDate
     * @param orgType
     * @return SlotDetail
     */
    public List<SlotDetail> findSlotsForScheduleIdAndOrganizationType(Long scheduleId, Date startDate, Date endDate, String orgType) {
        return slotRepository.findByScheduleReferenceAndEndDateTimeAfterAndStartDateTimeBeforeAndGpConnectBookableTrueAndBookableOrgTypes(scheduleId, startDate, endDate, orgType)
                .stream()
                .filter(slotEntity -> startDate == null || !slotEntity.getStartDateTime().before(startDate))
                .filter(slotEntity -> endDate == null || !slotEntity.getStartDateTime().after(endDate))
                .map(transformer::transform)
                .collect(Collectors.toList());
    }

    /**
     *
     * @param scheduleId
     * @param startDate
     * @param endDate
     * @return SlotDetail
     */
    public List<SlotDetail> findSlotsForScheduleIdNoOrganizationTypeOrODS(Long scheduleId, Date startDate, Date endDate) {
        return slotRepository.findByScheduleReferenceAndEndDateTimeAfterAndStartDateTimeBeforeAndGpConnectBookableTrue(scheduleId, startDate, endDate)
                .stream()
                .filter(slotEntity -> startDate == null || !slotEntity.getStartDateTime().before(startDate))
                .filter(slotEntity -> endDate == null || !slotEntity.getStartDateTime().after(endDate))
                .filter(slotEntity -> slotEntity.getBookableOrganizations().isEmpty())
                .filter(slotEntity -> slotEntity.getBookableOrgTypes().isEmpty())
                .map(transformer::transform)
                .collect(Collectors.toList());
    }

    /**
     *
     * @param scheduleId
     * @param startDate
     * @param endDate
     * @return SlotDetail
     */
    public List<SlotDetail> findSlotsForScheduleId(Long scheduleId, Date startDate, Date endDate) {
        return slotRepository.findByScheduleReferenceAndEndDateTimeAfterAndStartDateTimeBeforeAndGpConnectBookableTrue(scheduleId, startDate, endDate)
                .stream()
                .filter(slotEntity -> startDate == null || !slotEntity.getStartDateTime().before(startDate))
                .filter(slotEntity -> endDate == null || !slotEntity.getStartDateTime().after(endDate))
                .map(transformer::transform)
                .collect(Collectors.toList());
    }
}
