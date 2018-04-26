package uk.gov.hscic.patient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.AllergyIntolerance;
import org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceCategory;
import org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceClinicalStatus;
import org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceReactionComponent;
import org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceSeverity;
import org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceVerificationStatus;
import org.hl7.fhir.dstu3.model.ListResource.ListEntryComponent;
import org.hl7.fhir.dstu3.model.ListResource.ListMode;
import org.hl7.fhir.dstu3.model.ListResource.ListStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.gov.hscic.SystemConstants;
import uk.gov.hscic.SystemURL;
import uk.gov.hscic.patient.details.PatientRepository;
import uk.gov.hscic.patient.structuredAllergyIntolerance.StructuredAllergyIntoleranceEntity;
import uk.gov.hscic.patient.structuredAllergyIntolerance.StructuredAllergySearch;
import uk.gov.hscic.practitioner.PractitionerSearch;

@Component
public class StructuredAllergyIntoleranceBuilder {

    @Autowired
    private StructuredAllergySearch structuredAllergySearch;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PractitionerSearch practitionerSearch;

    public Bundle buildStructuredAllergyIntolerence(String NHS, Bundle bundle, Boolean includedResolved) {

        List<StructuredAllergyIntoleranceEntity> allergyData = structuredAllergySearch.getAllergyIntollerence(NHS);

        ListResource active = initiateListResource(NHS, "Active Allergies");
        ListResource resolved = initiateListResource(NHS, "Resolved Allergies");

        AllergyIntolerance allergyIntolerance;

        if (allergyData.size() == 1 &&
                allergyData.get(0).getClinicalStatus().equals(SystemConstants.NO_KNOWN)) {

            CodeableConcept noKnownAllergies = createCoding(SystemURL.HL7_SPECIAL_VALUES, "nil-known", "Nil Known");
            noKnownAllergies.setText("No Known Allergies");
            active.setEmptyReason(noKnownAllergies);

            Reference patient = new Reference(SystemConstants.PATIENT_REFERENCE_URL + allergyData.get(0).getPatientRef());

            active.setSubject(patient);
            bundle.addEntry().setResource(active);
            
            if (includedResolved) {
            	resolved.setSubject(patient);
            	resolved.setEmptyReason(noKnownAllergies);
            	bundle.addEntry().setResource(resolved);
            }
            
            return bundle;
        }

        for (StructuredAllergyIntoleranceEntity allergyIntoleranceEntity : allergyData) {
            allergyIntolerance = new AllergyIntolerance();

            allergyIntolerance.setMeta(createMeta(SystemURL.SD_CC_ALLERGY_INTOLERANCE));

            allergyIntolerance.setId(allergyIntoleranceEntity.getId().toString());

            if (allergyIntoleranceEntity.getClinicalStatus().equals(SystemConstants.ACTIVE)) {
                allergyIntolerance.setClinicalStatus(AllergyIntoleranceClinicalStatus.ACTIVE);
            } else {
                allergyIntolerance.setClinicalStatus(AllergyIntoleranceClinicalStatus.RESOLVED);
            }

            if (allergyIntoleranceEntity.getClinicalStatus().equals(SystemConstants.MEDICATION)) {
                allergyIntolerance.addCategory(AllergyIntoleranceCategory.MEDICATION);
            } else {
                allergyIntolerance.addCategory(AllergyIntoleranceCategory.ENVIRONMENT);
            }

            allergyIntolerance.setVerificationStatus(AllergyIntoleranceVerificationStatus.UNCONFIRMED);

            CodeableConcept value = new CodeableConcept();
            Coding coding = new Coding();
            coding.setCode(allergyIntoleranceEntity.getCoding());
            coding.setDisplay(allergyIntoleranceEntity.getDisplay());
            coding.setSystem(SystemConstants.SNOMED_URL);
            value.addCoding(coding);

            allergyIntolerance.setCode(value);

            allergyIntolerance.setAssertedDate(allergyIntoleranceEntity.getAssertedDate());

            Reference patient = new Reference(
                    SystemConstants.PATIENT_REFERENCE_URL + allergyIntoleranceEntity.getPatientRef());
            allergyIntolerance.setPatient(patient);

            Annotation noteAnnotation = new Annotation(new StringType(allergyIntoleranceEntity.getNote()));
            allergyIntolerance.setNote(Collections.singletonList(noteAnnotation));
            
            AllergyIntoleranceReactionComponent reaction = new AllergyIntoleranceReactionComponent();

            // MANIFESTATION
            List<CodeableConcept> theManifestation = new ArrayList<>();
            CodeableConcept manifestation = new CodeableConcept();
            Coding manifestationCoding = new Coding();
            manifestationCoding.setDisplay(allergyIntoleranceEntity.getManifestationDisplay());
            manifestationCoding.setCode(allergyIntoleranceEntity.getManifestationCoding());
            manifestationCoding.setSystem(SystemConstants.SNOMED_URL);
            manifestation.addCoding(manifestationCoding);
            theManifestation.add(manifestation);
            reaction.setManifestation(theManifestation);

            reaction.setDescription(allergyIntoleranceEntity.getNote());

            AllergyIntoleranceSeverity severity = AllergyIntoleranceSeverity.SEVERE;
            reaction.setSeverity(severity);

            CodeableConcept exposureRoute = new CodeableConcept();
            reaction.setExposureRoute(exposureRoute);
            allergyIntolerance.addReaction(reaction);

            final Reference refValue = new Reference();
            final Identifier identifier = new Identifier();
            final String recorder = allergyIntoleranceEntity.getRecorder();

            // TODO: This is just an example to demonstrate using Reference element instead of Identifier element

            if(recorder.equals("9476719931")) {
                Reference rec = new Reference(
                        SystemConstants.PATIENT_REFERENCE_URL + allergyIntoleranceEntity.getPatientRef());
                allergyIntolerance.setRecorder(rec);
            }
            else if(patientRepository.findByNhsNumber(recorder) != null) {
                identifier.setSystem(SystemURL.ID_NHS_NUMBER);
                identifier.setValue(recorder);

                refValue.setIdentifier(identifier);
                allergyIntolerance.setRecorder(refValue);
            } else if (practitionerSearch.findPractitionerByUserId(recorder) != null) {
                identifier.setSystem(SystemURL.SD_EXTENSION_GPC_PRACTITIONER_ROLE);
                identifier.setValue(recorder);

                refValue.setIdentifier(identifier);
                allergyIntolerance.setRecorder(refValue);
            }

            if (allergyIntolerance.getClinicalStatus().getDisplay().contains("Active")) {
                listResourceBuilder(active, allergyIntolerance);
                bundle.addEntry().setResource(allergyIntolerance);

            } else if (allergyIntolerance.getClinicalStatus().getDisplay().equals("Resolved")
                    && includedResolved.equals(true)) {
                listResourceBuilder(resolved, allergyIntolerance);
                allergyIntolerance.setLastOccurrence(allergyIntoleranceEntity.getEndDate());

                allergyIntolerance.setExtension(createAllergyEndExtension(allergyIntoleranceEntity));

                bundle.addEntry().setResource(allergyIntolerance);
            }
        }

        if (!active.hasEntry()) {
            addEmptyListNote(active);
            addEmptyReasonCode(active);
        }

        bundle.addEntry().setResource(active);

        if (includedResolved && !resolved.hasEntry()) {
            addEmptyListNote(resolved);
            addEmptyReasonCode(resolved);
        }

        if (includedResolved) {
            bundle.addEntry().setResource(resolved);
        }

        return bundle;

    }

    private ListResource initiateListResource(String NHS, String display) {
        ListResource listResource = new ListResource();

        listResource.setCode(createCoding("http://snomed.info/sct", "TBD", display));
        listResource.setMeta(createMeta(SystemURL.SD_GPC_LIST));
        listResource.setId("33");
        listResource.setStatus(ListStatus.CURRENT);
        listResource.setMode(ListMode.SNAPSHOT);
        addSubjectWithIdentifier(NHS, listResource);
        listResource.setTitle(display);
        return listResource;
    }

    private void addEmptyListNote(ListResource list) {
        list.addNote(new Annotation(new StringType("" +
                "There are no allergies in the patient record but it has not been confirmed with the patient that " +
                "they have no allergies (that is, a ‘no known allergies’ code has not been recorded)."
        )));
    }

        private void addEmptyReasonCode(ListResource list) {
        CodeableConcept noContent = new CodeableConcept();
        noContent.setText(SystemConstants.NO_CONTENT);
        list.setEmptyReason(noContent);
    }

    private void addSubjectWithIdentifier(String NHS, ListResource active) {
        final Reference value = new Reference();
        final Identifier identifier = new Identifier();
        identifier.setSystem(SystemURL.ID_NHS_NUMBER);
        identifier.setValue(NHS);

        value.setIdentifier(identifier);
        active.setSubject(value);
    }

    private CodeableConcept createCoding(String system, String code, String display) {
        final CodeableConcept codeableConcept = new CodeableConcept();
        codeableConcept.setCoding(Arrays.asList(new Coding(
                system,
                code,
                display
        )));

        return codeableConcept;
    }

    private Meta createMeta(String profile) {
        final Meta meta = new Meta();
        meta.addProfile(profile);
        meta.setVersionId("3");

        return meta;
    }

    private List<Extension> createAllergyEndExtension(StructuredAllergyIntoleranceEntity allergyIntoleranceEntity) {
        final Extension allergyEnd = new Extension("https://fhir.nhs.uk/STU3/StructureDefinition/Extension-CareConnect-GPC-AllergyIntoleranceEnd-1");

        final Extension endDate = new Extension("endDate", new DateTimeType(allergyIntoleranceEntity.getEndDate()));

        final Extension endReason = new Extension("endReason", new StringType(allergyIntoleranceEntity.getEndReason()));

        allergyEnd.addExtension(endDate);
        allergyEnd.addExtension(endReason);

        return Arrays.asList(allergyEnd);
    }

    private void listResourceBuilder(ListResource buildingListResource, AllergyIntolerance allergyIntolerance) {
        buildingListResource.setId(allergyIntolerance.getId());

        List<Resource> value = new ArrayList<>();
        value.add(allergyIntolerance);
        buildingListResource.setContained(value);
        ListEntryComponent comp = new ListEntryComponent();
        Reference allergyReference = new Reference("AllergyIntolerance/" + allergyIntolerance.getId());
        comp.setItem(allergyReference);
        buildingListResource.addEntry(comp);
    }

}