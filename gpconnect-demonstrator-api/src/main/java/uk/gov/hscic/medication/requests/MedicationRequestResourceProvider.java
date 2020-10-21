package uk.gov.hscic.medication.requests;

import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import java.util.ArrayList;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestDispenseRequestComponent;
import org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestIntent;
import org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestRequesterComponent;
import org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestStatus;
import org.hl7.fhir.exceptions.FHIRException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hscic.SystemURL;
import uk.gov.hscic.medication.request.MedicationRequestEntityToDetailTransformer;
import uk.gov.hscic.medication.request.MedicationRequestRepository;
import uk.gov.hscic.model.medication.MedicationRequestDetail;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import static uk.gov.hscic.SystemConstants.NO_INFORMATION_AVAILABLE;
import static uk.gov.hscic.SystemURL.ID_CROSS_CARE_SETTIING;

@Component
public class MedicationRequestResourceProvider {

    @Autowired
    private MedicationRequestRepository medicationRequestRepository;

    @Autowired
    private MedicationRequestEntityToDetailTransformer medicationRequestEntityToDetailTransformer;

    public MedicationRequest getMedicationRequestPlanResource(String medicationRequestId) {
        MedicationRequestDetail requestDetail = medicationRequestEntityToDetailTransformer
                .transform(medicationRequestRepository.findById(Long.parseLong(medicationRequestId)).get());

        MedicationRequest medicationRequest = getMedicationRequestFromDetail(requestDetail);

        return medicationRequest;
    }

    public List<MedicationRequest> getMedicationRequestOrderResources(String groupIdentifier) {
        return medicationRequestRepository.findByIntentCodeAndGroupIdentifier("order", groupIdentifier)
                .stream().map(requestEntity -> medicationRequestEntityToDetailTransformer.transform(requestEntity))
                .sorted(Comparator.comparing(requestDetail -> ((MedicationRequestDetail) requestDetail).getDispenseRequestStartDate()))
                .map(requestDetail -> getMedicationRequestFromDetail(requestDetail))
                .collect(Collectors.toList());
    }

    private MedicationRequest getMedicationRequestFromDetail(MedicationRequestDetail requestDetail) {
        MedicationRequest medicationRequest = new MedicationRequest();

        medicationRequest.setId(requestDetail.getId().toString());
        List<Identifier> identifiers = new ArrayList<>();
        Identifier identifier = new Identifier()
                .setSystem(ID_CROSS_CARE_SETTIING)
                .setValue(requestDetail.getGuid());
        identifiers.add(identifier);
        medicationRequest.setIdentifier(identifiers);
        medicationRequest.setMeta(new Meta().addProfile(SystemURL.SD_GPC_MEDICATION_REQUEST));
        setBasedOnReferences(medicationRequest, requestDetail);
        if (requestDetail.getPrescriptionTypeCode().contains("repeat")) {
            medicationRequest.setGroupIdentifier(new Identifier().setValue(requestDetail.getGroupIdentifier()));
        }

        try {
            medicationRequest.setStatus(MedicationRequestStatus.fromCode(requestDetail.getStatusCode()));
        } catch (FHIRException e) {
            throw new UnprocessableEntityException(e.getMessage());
        }

        try {
            medicationRequest.setIntent(MedicationRequestIntent.fromCode(requestDetail.getIntentCode()));
        } catch (FHIRException e) {
            throw new UnprocessableEntityException(e.getMessage());
        }

        if (requestDetail.getMedicationId() != null) {
            medicationRequest.setMedication(new Reference(new IdType("Medication", requestDetail.getMedicationId())));
        }

        if (requestDetail.getPatientId() != null) {
            medicationRequest.setSubject(new Reference(new IdType("Patient", requestDetail.getPatientId())));
        }

        if (requestDetail.getAuthorisingPractitionerId() != null) {
            medicationRequest.setRecorder(new Reference(new IdType("Practitioner", requestDetail.getAuthorisingPractitionerId())));
        }

        if (requestDetail.getPriorMedicationRequestId() != null) {
            medicationRequest.setPriorPrescription(new Reference(new IdType("MedicationRequest", requestDetail.getPriorMedicationRequestId())));
        }

        medicationRequest.setAuthoredOn(requestDetail.getAuthoredOn());
        medicationRequest.setDispenseRequest(getDispenseRequestComponent(requestDetail));
        //medicationRequest.setRequester(getRequesterComponent(requestDetail)); //TODO - spec needs to clarify whether this should be populated or not

        // #269 do not set MedicationRequest ReasonCode
        //setReasonCodes(medicationRequest, requestDetail);
        setNotes(medicationRequest, requestDetail);
	if (medicationRequest.getIntent() != MedicationRequestIntent.ORDER) {
            setRepeatInformation(medicationRequest, requestDetail);
        }
        setPrescriptionType(medicationRequest, requestDetail);
        setStatusReason(medicationRequest, requestDetail);

        String dosageInstructionText = requestDetail.getDosageText();
        medicationRequest.addDosageInstruction(new Dosage()
                .setText(dosageInstructionText == null || dosageInstructionText.trim().isEmpty() ? NO_INFORMATION_AVAILABLE : dosageInstructionText)
                .setPatientInstruction(requestDetail.getDosageInstructions()));

        return medicationRequest;
    }

    private void setPrescriptionType(MedicationRequest medicationRequest, MedicationRequestDetail requestDetail) {
        String prescriptionCode = requestDetail.getPrescriptionTypeCode();
        Coding prescriptionTypeCoding = null;
        if (prescriptionCode != null && !prescriptionCode.trim().isEmpty()) {
            prescriptionTypeCoding = new Coding(SystemURL.CS_CC_PRESCRIPTION_TYPE_STU3, prescriptionCode, requestDetail.getPrescriptionTypeDisplay());
        } else {
            prescriptionTypeCoding = new Coding(SystemURL.CS_CC_PRESCRIPTION_TYPE_STU3, NO_INFORMATION_AVAILABLE, NO_INFORMATION_AVAILABLE);
        }
        Extension prescriptionTypeExtension = new Extension(SystemURL.SD_CC_EXT_MEDICATION_PRESCRIPTION_TYPE, new CodeableConcept().addCoding(prescriptionTypeCoding));
        medicationRequest.addExtension(prescriptionTypeExtension);
    }

    private void setStatusReason(MedicationRequest medicationRequest, MedicationRequestDetail requestDetail) {
        if (medicationRequest.getStatus().equals(MedicationRequestStatus.STOPPED)) {
            Extension statusReasonExtension = new Extension(SystemURL.SD_CC_EXT_MEDICATION_STATUS_REASON);
            statusReasonExtension.addExtension(new Extension("statusReason", new StringType(requestDetail.getStatusReason())));
            Date statusReasonDate = requestDetail.getStatusReasonDate();
            if (statusReasonDate != null) {
                statusReasonExtension.addExtension(new Extension("statusChangeDate", new DateTimeType(statusReasonDate)));
            } else {
                statusReasonExtension.addExtension(new Extension("statusChangeDate", new StringType(NO_INFORMATION_AVAILABLE)));
            }
            medicationRequest.addExtension(statusReasonExtension);
        }
    }

    private void setBasedOnReferences(MedicationRequest medicationRequest, MedicationRequestDetail requestDetail) {
        if (requestDetail.getIntentCode().equals("order")) {
            requestDetail.getBasedOnReferences().forEach(reference -> {
                if (reference.getReferenceUrl().equals(SystemURL.SD_GPC_MEDICATION_REQUEST)) {
                    medicationRequest.addBasedOn(new Reference(new IdType("MedicationRequest", reference.getReferenceId())));
                }
            });
        }
    }

    // #269 dont set reason code
//	private void setReasonCodes(MedicationRequest medicationRequest, MedicationRequestDetail requestDetail) {
//		requestDetail.getReasonCodes().forEach(rc -> {
//			Coding coding = new Coding(SystemURL.VS_SNOMED, rc.getCode(), rc.getDisplay());
//			medicationRequest.addReasonCode(new CodeableConcept().addCoding(coding));
//		});
//	}
    private void setNotes(MedicationRequest medicationRequest, MedicationRequestDetail requestDetail) {
        requestDetail.getNotes().forEach(note -> {
            Annotation annotation = new Annotation();
            annotation.setId(String.valueOf(note.getId()));
            annotation.setText(note.getNote());
            annotation.setTime(note.getDateWritten());
            if (note.getAuthorReferenceUrl().equals(SystemURL.SD_GPC_PRACTITIONER)) {
                annotation.setAuthor(new Reference(new IdType("Practitioner", note.getAuthorId())));
            } else if (note.getAuthorReferenceUrl().equals(SystemURL.SD_GPC_PATIENT)) {
                annotation.setAuthor(new Reference(new IdType("Patient", note.getAuthorId())));
            }

            medicationRequest.addNote(annotation);
        });
    }

    private void setRepeatInformation(MedicationRequest medicationRequest, MedicationRequestDetail requestDetail) {
        // #271 add repeat info repeat *or* repeat-dispensing
        if (requestDetail.getPrescriptionTypeCode().equals("repeat")||requestDetail.getPrescriptionTypeCode().equals("repeat-dispensing")) {
            Extension repeatInformationExtension = new Extension(SystemURL.SD_CC_EXT_MEDICATION_REPEAT_INFORMATION);
            if (requestDetail.getNumberOfRepeatPrescriptionsAllowed() != null) {
                repeatInformationExtension.addExtension(new Extension("numberOfRepeatPrescriptionsAllowed", new PositiveIntType(requestDetail.getNumberOfRepeatPrescriptionsAllowed())));
            } else {
                repeatInformationExtension.addExtension(new Extension("authorisationExpiryDate", new DateTimeType(requestDetail.getAuthorisationExpiryDate())));
            }
            repeatInformationExtension.addExtension(new Extension("numberOfRepeatPrescriptionsIssued", new PositiveIntType(requestDetail.getNumberOfRepeatPrescriptionsIssued())));

            medicationRequest.addExtension(repeatInformationExtension);
        }
    }

    private MedicationRequestDispenseRequestComponent getDispenseRequestComponent(
            MedicationRequestDetail requestDetail) {
        MedicationRequestDispenseRequestComponent dispenseRequest = new MedicationRequestDispenseRequestComponent();

        Period period = new Period().setStart(requestDetail.getDispenseRequestStartDate());
        if (requestDetail.getDispenseRequestEndDate() != null) {
            period.setEnd(requestDetail.getDispenseRequestEndDate());
        }
        dispenseRequest.setValidityPeriod(period);

        setDispenseQuantity(dispenseRequest, requestDetail);

        Duration duration = new Duration();
        duration.setSystem(SystemURL.CS_UNITS_OF_MEASURE);
        duration.setCode("d");
        duration.setValue(requestDetail.getExpectedSupplyDuration());
        duration.setUnit("day");
        dispenseRequest.setExpectedSupplyDuration(duration); //TODO - spec needs to clarify whether this should be populated or not

        dispenseRequest.setPerformer(new Reference(new IdType("Organization", requestDetail.getDispenseRequestOrganizationId())));

        return dispenseRequest;
    }

    private void setDispenseQuantity(MedicationRequestDispenseRequestComponent dispenseRequest,
            MedicationRequestDetail requestDetail) {
        SimpleQuantity quantity = new SimpleQuantity();
        if (requestDetail.getDispenseQuantityText() == null) {
            quantity.setValue(requestDetail.getDispenseQuantityValue());
            quantity.setUnit(requestDetail.getDispenseQuantityUnit());
        } else {
            quantity.addExtension(new Extension(SystemURL.SD_CC_EXT_MEDICATION_QUANTITY_TEXT, new StringType(requestDetail.getDispenseQuantityText())));
        }
        dispenseRequest.setQuantity(quantity);
    }

    //TODO - spec needs to clarify whether this should be populated or not
    private MedicationRequestRequesterComponent getRequesterComponent(MedicationRequestDetail requestDetail) {
        MedicationRequestRequesterComponent requesterComponent = new MedicationRequestRequesterComponent();
        switch (requestDetail.getRequesterUrl()) {
            case (SystemURL.SD_GPC_PATIENT):
                requesterComponent.setAgent(new Reference(new IdType("Patient", requestDetail.getRequesterId())));
                break;
            case (SystemURL.SD_GPC_PRACTITIONER):
                requesterComponent.setAgent(new Reference(new IdType("Practitioner", requestDetail.getRequesterId())));
                break;
            case (SystemURL.SD_GPC_ORGANIZATION):
                requesterComponent.setAgent(new Reference(new IdType("Organization", requestDetail.getRequesterId())));
                break;
            default:
                break;
        }
        requesterComponent.setOnBehalfOf(new Reference(new IdType("Organization", requestDetail.getDispenseRequestOrganizationId())));

        return requesterComponent;
    }
}
