package uk.gov.hscic.medications;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.gov.hscic.SystemConstants;
import uk.gov.hscic.SystemURL;
import uk.gov.hscic.common.helpers.CodeableConceptBuilder;
import uk.gov.hscic.medication.statement.MedicationStatementEntity;
import uk.gov.hscic.medication.statement.MedicationStatementRepository;
import uk.gov.hscic.model.medication.MedicationDetail;
import uk.gov.hscic.patient.details.PatientRepository;
import uk.gov.hscic.patient.structuredAllergyIntolerance.StructuredAllergyIntoleranceEntity;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class MedicationResourceProvider implements IResourceProvider {

    @Autowired
    private MedicationRepository medicationRepository;
    
    @Autowired
    private MedicationEntityToDetailTransformer medicationEntityToDetailTransformer;

	@Autowired
	private PatientRepository patientRepository;

	@Autowired
	private MedicationStatementRepository medicationStatementRepository;
	
    @Autowired
    private CodeableConceptBuilder codeableConceptBuilder;

    @Override
    public Class<Medication> getResourceType() {
        return Medication.class;
    }

    @Read()
    public Medication getMedicationById(@IdParam IdType medicationId) {
        MedicationEntity medicationEntity = medicationRepository.findById(medicationId.getIdPartAsLong()).get();

        if (medicationEntity == null) {
            OperationOutcome operationalOutcome = new OperationOutcome();
            operationalOutcome.addIssue().setSeverity(IssueSeverity.ERROR).setDiagnostics("No medication details found for ID: " + medicationId.getIdPart());
            throw new InternalErrorException("No medication details found for ID: " + medicationId.getIdPart(), operationalOutcome);
        }

        Medication medication = new Medication();

        String resourceId = String.valueOf(medicationEntity.getId());
        String versionId = String.valueOf(new Date());
        String resourceType = medication.getResourceType().toString();

        IdType id = new IdType(resourceType, resourceId, versionId);

        medication.setId(id);
                
        return medication;
    }
    
    public Medication getMedicationResourceForBundle(Long medicationId) {
		MedicationDetail medicationDetail = medicationEntityToDetailTransformer
				.transform(medicationRepository.findById(medicationId).get());
		
		Medication medication = new Medication();
		
		medication.setId(new IdType(medicationDetail.getId()));
		
		medication.setMeta(new Meta().addProfile(SystemURL.SD_GPC_MEDICATION));
		
		codeableConceptBuilder.addConceptCode(SystemConstants.SNOMED_URL, medicationDetail.getConceptCode(), medicationDetail.getConceptDisplay())
        	   .addDescription(medicationDetail.getDescCode(), medicationDetail.getDescDisplay())
        	   .addTranslation(medicationDetail.getCodeTranslationRef());
        CodeableConcept code = codeableConceptBuilder.build();
        codeableConceptBuilder.clear();
		code.setText(medicationDetail.getText());
		medication.setCode(code);
		
		return medication;
	}

	public Map<String,List<String>> getAllMedicationAndAllergiesForPatient(String nhsNumber) {
		Map<String,List<String>> allergiesAssociatedWithMedicationMap = new HashMap<>();
		for (MedicationEntity medicationEntity:medicationRepository.findAll()) {
			List<StructuredAllergyIntoleranceEntity> allergyEntities = medicationEntity.getMedicationAllergies();
			if(allergyEntities.size() > 0) {
				for (StructuredAllergyIntoleranceEntity allergy :allergyEntities) {

					if(Objects.equals(allergy.getNhsNumber(), nhsNumber)) {
						allergiesAssociatedWithMedicationMap.computeIfAbsent(medicationEntity.getConceptDisplay(), k-> new ArrayList<>()).add(allergy.getConceptDisplay());
					} else {
						allergiesAssociatedWithMedicationMap.put(medicationEntity.getConceptDisplay(), Collections.emptyList());
					}


				}
			} else {
				allergiesAssociatedWithMedicationMap.put(medicationEntity.getConceptDisplay(), Collections.emptyList());
			}
		}
		return allergiesAssociatedWithMedicationMap;
	}

	public MedicationStatementEntity addMedicationStatement(LinkedHashMap data) throws ParseException {
		String pattern = "yyyy-MM-dd";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		System.out.println(data);

		MedicationStatementEntity medicationStatementEntity = new MedicationStatementEntity();
		medicationStatementEntity.setDateAsserted(simpleDateFormat.parse("2017-06-12"));
		medicationStatementEntity.setDosageInstruction("Take with meals");
		medicationStatementEntity.setDosageText("Take 2 tablets every 4 hours");
		medicationStatementEntity.setEndDate(simpleDateFormat.parse("2018-06-12"));
		medicationStatementEntity.setLastIssueDate(simpleDateFormat.parse("2017-06-12"));
		medicationStatementEntity.setDosageLastChanged(simpleDateFormat.parse("2018-03-15"));
        medicationStatementEntity.setMedicationRequestId("1");
		medicationStatementEntity.setMedicationId(medicationRepository.getMedicationIdByName((String) data.get("medication"))); // get from frontend
		medicationStatementEntity.setPatientId(patientRepository.getPatientIdByNhsNumbwer(String.valueOf(data.get("nhsNumber")))); // get from frontend

		return medicationStatementRepository.save(medicationStatementEntity);
	}
}
