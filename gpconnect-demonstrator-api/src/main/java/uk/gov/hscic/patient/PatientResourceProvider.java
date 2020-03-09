package uk.gov.hscic.patient;

import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.Count;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.param.DateAndListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnclassifiedServerFailureException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import static java.lang.Integer.min;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import org.hl7.fhir.dstu3.model.Address.AddressType;
import org.hl7.fhir.dstu3.model.Address.AddressUse;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.Bundle.BundleType;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointUse;
import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.dstu3.model.HumanName.NameUse;
import org.hl7.fhir.dstu3.model.Identifier.IdentifierUse;
import org.hl7.fhir.dstu3.model.OperationOutcome.IssueType;
import org.hl7.fhir.dstu3.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.exceptions.FHIRException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hscic.OperationOutcomeFactory;
import uk.gov.hscic.SystemCode;
import uk.gov.hscic.SystemConstants;
import uk.gov.hscic.SystemURL;
import uk.gov.hscic.appointments.AppointmentResourceProvider;
import uk.gov.hscic.common.helpers.StaticElementsHelper;
import uk.gov.hscic.common.validators.IdentifierValidator;
import uk.gov.hscic.medications.PopulateMedicationBundle;
import uk.gov.hscic.model.organization.OrganizationDetails;
import uk.gov.hscic.model.patient.PatientDetails;
import uk.gov.hscic.organization.OrganizationResourceProvider;
import uk.gov.hscic.organization.OrganizationSearch;
import uk.gov.hscic.patient.details.PatientSearch;
import uk.gov.hscic.patient.details.PatientStore;
import uk.gov.hscic.practitioner.PractitionerResourceProvider;
import uk.gov.hscic.practitioner.PractitionerRoleResourceProvider;
import uk.gov.hscic.util.NhsCodeValidator;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static org.hl7.fhir.dstu3.model.Address.AddressUse.OLD;
import static org.hl7.fhir.dstu3.model.Address.AddressUse.WORK;
import org.hl7.fhir.dstu3.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.dstu3.model.Patient.ContactComponent;
import org.springframework.beans.factory.annotation.Value;
import static uk.gov.hscic.SystemURL.SD_CC_EXT_NHS_COMMUNICATION;
import static uk.gov.hscic.SystemURL.VS_GPC_ERROR_WARNING_CODE;
import uk.gov.hscic.model.telecom.TelecomDetails;
import static uk.gov.hscic.common.filters.FhirRequestGenericIntercepter.throwInvalidRequest400_BadRequestException;
import static uk.gov.hscic.common.filters.FhirRequestGenericIntercepter.throwUnprocessableEntity422_InvalidResourceException;

@Component
public class PatientResourceProvider implements IResourceProvider {

    public static final String REGISTER_PATIENT_OPERATION_NAME = "$gpc.registerpatient";
    public static final String GET_CARE_RECORD_OPERATION_NAME = "$gpc.getcarerecord";
    public static final String GET_STRUCTURED_RECORD_OPERATION_NAME = "$gpc.getstructuredrecord";

    private static final String TEMPORARY_RESIDENT_REGISTRATION_TYPE = "T";
    private static final String ACTIVE_REGISTRATION_STATUS = "A";

    private static final int ADDRESS_CITY_INDEX = 3;
    private static final int ADDRESS_DISTRICT_INDEX = 4;

    @Autowired
    private PractitionerResourceProvider practitionerResourceProvider;

    @Autowired
    private PractitionerRoleResourceProvider practitionerRoleResourceProvider;

    @Autowired
    private AppointmentResourceProvider appointmentResourceProvider;

    @Autowired
    private OrganizationResourceProvider organizationResourceProvider;

    @Autowired
    private PatientStore patientStore;

    @Autowired
    private PatientSearch patientSearch;

    @Autowired
    private OrganizationSearch organizationSearch;

    @Autowired
    private StaticElementsHelper staticElHelper;

    @Autowired
    private StructuredAllergyIntoleranceBuilder structuredAllergyIntoleranceBuilder;

    @Autowired
    private PopulateMedicationBundle populateMedicationBundle;

    @Value("${datasource.patient.notOnSpine:#{null}}")
    private String patientNotOnSpine;

    @Value("${datasource.patient.superseded:#{null}}")
    private String patientSuperseded;

    @Value("${datasource.patient.nhsNumber:#{null}}")
    private String patient2;

    @Value("${datasource.patient.noconsent:#{null}}")
    private String patientNoconsent;

    private NhsNumber nhsNumber;

    private Map<String, Boolean> registerPatientParams;

    private OperationOutcome operationOutcome;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public static Set<String> getCustomReadOperations() {
        Set<String> customReadOperations = new HashSet<>();
        customReadOperations.add(GET_CARE_RECORD_OPERATION_NAME);

        return customReadOperations;
    }

    public static Set<String> getCustomWriteOperations() {
        Set<String> customWriteOperations = new HashSet<>();
        customWriteOperations.add(REGISTER_PATIENT_OPERATION_NAME);

        return customWriteOperations;
    }

    @Override
    public Class<Patient> getResourceType() {
        return Patient.class;
    }

    @PostConstruct
    public void postConstruct() {
        nhsNumber = new NhsNumber();

        registerPatientParams = new HashMap<>();
        registerPatientParams.put("registerPatient", true);

        // Spring does not strip trailing blanks from property values
        patientNotOnSpine = patientNotOnSpine.trim();
        patientSuperseded = patientSuperseded.trim();
        patientNoconsent = patientNoconsent.trim();
        patient2 = patient2.trim();
    }

    @Read(version = true)
    public Patient getPatientById(@IdParam IdType internalId) throws FHIRException {
        PatientDetails patientDetails = patientSearch.findPatientByInternalID(internalId.getIdPart());

        if (patientDetails == null || patientDetails.isSensitive() || patientDetails.isDeceased() || !patientDetails.isActive()) {
            throw OperationOutcomeFactory.buildOperationOutcomeException(
                    new ResourceNotFoundException("No patient details found for patient ID: " + internalId.getIdPart()),
                    SystemCode.PATIENT_NOT_FOUND, IssueType.NOTFOUND);
        }

        Patient patient = IdentifierValidator.versionComparison(internalId,
                patientDetailsToPatientResourceConverter(patientDetails));
        if (null != patient) {
            addPreferredBranchSurgeryExtension(patient);
        }
        return patient;
    }

    @Search
    public List<Patient> getPatientsByPatientId(@RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam tokenParam)
            throws FHIRException {

        Patient patient = getPatientByPatientId(nhsNumber.fromToken(tokenParam));
        if (null != patient) {
            addPreferredBranchSurgeryExtension(patient);
        }

        PatientDetails patientDetails = patientSearch.findPatient(nhsNumber.fromToken(tokenParam));

        // ie does not return a deceased, inactive or sensitive patient in the list 
        return null == patient || patient.getDeceased() != null || !patientDetails.isActive() || patientDetails.isSensitive() ? Collections.emptyList()
                : Collections.singletonList(patient);
    }

    private void addPreferredBranchSurgeryExtension(Patient patient) {
        List<Extension> regDetailsEx = patient.getExtensionsByUrl(SystemURL.SD_EXTENSION_CC_REG_DETAILS);
        Extension branchSurgeryEx = regDetailsEx.get(0).addExtension();
        branchSurgeryEx.setUrl("preferredBranchSurgery");
        branchSurgeryEx.setValue(new Reference("Location/1"));
    }

    private Patient getPatientByPatientId(String patientId) throws FHIRException {
        PatientDetails patientDetails = patientSearch.findPatient(patientId);

        return null == patientDetails ? null : patientDetailsToPatientResourceConverter(patientDetails);
    }

    private void validateParameterNames(Parameters parameters, Map<String, Boolean> parameterDefinitions) {
        List<String> parameterNames = parameters.getParameter().stream().map(ParametersParameterComponent::getName)
                .collect(Collectors.toList());

        Set<String> parameterDefinitionNames = parameterDefinitions.keySet();

        if (parameterNames.isEmpty() == false) {
            for (String parameterDefinition : parameterDefinitionNames) {
                boolean mandatory = parameterDefinitions.get(parameterDefinition);

                if (mandatory) {
                    if (parameterNames.contains(parameterDefinition) == false) {
                        throw OperationOutcomeFactory.buildOperationOutcomeException(
                                new InvalidRequestException("Not all mandatory parameters have been provided"),
                                SystemCode.INVALID_PARAMETER, IssueType.INVALID);
                    }
                }
            }

            if (parameterDefinitionNames.containsAll(parameterNames) == false) {
                parameterNames.removeAll(parameterDefinitionNames);
                throwInvalidRequest400_BadRequestException("Unrecognised parameters have been provided - " + parameterNames.toString());
            }
        } else {
            throw OperationOutcomeFactory.buildOperationOutcomeException(
                    new InvalidRequestException("Not all mandatory parameters have been provided"),
                    SystemCode.INVALID_PARAMETER, IssueType.INVALID);
        }
    }

    @Search(compartmentName = "Appointment")
    public List<Appointment> getPatientAppointments(@IdParam IdType patientLocalId, @Sort SortSpec sort,
            @Count Integer count, @OptionalParam(name = "start") DateAndListParam startDate) {
        return appointmentResourceProvider.getAppointmentsForPatientIdAndDates(patientLocalId, sort, count, startDate);
    }

    @Operation(name = GET_STRUCTURED_RECORD_OPERATION_NAME)
    public Bundle StructuredRecordOperation(@ResourceParam Parameters params) throws FHIRException {
        Bundle structuredBundle = new Bundle();
        Boolean getAllergies = false;
        Boolean includeResolved = false;
        Boolean getMedications = false;
        Boolean includePrescriptionIssues = false;
        Period medicationPeriod = null;

        String NHS = getNhsNumber(params);

        PatientDetails patientDetails = patientSearch.findPatient(NHS);

        // see https://nhsconnect.github.io/gpconnect/accessrecord_structured_development_retrieve_patient_record.html#error-handling
        if (patientDetails == null || patientDetails.isSensitive() || patientDetails.isDeceased() || !patientDetails.isActive()) {
            throw OperationOutcomeFactory.buildOperationOutcomeException(
                    new ResourceNotFoundException("No patient details found for patient ID: " + NHS),
                    SystemCode.PATIENT_NOT_FOUND, IssueType.NOTFOUND);
        }

        if (NHS.equals(patientNoconsent)) {
            throw OperationOutcomeFactory.buildOperationOutcomeException(
                    new ForbiddenOperationException("No patient consent to share for patient ID: " + NHS),
                    SystemCode.NO_PATIENT_CONSENT, IssueType.FORBIDDEN);
        }

        operationOutcome = null;
        for (ParametersParameterComponent param : params.getParameter()) {
            if (validateParametersName(param.getName())) {
                if (param.getName().equals(SystemConstants.INCLUDE_ALLERGIES)) {
                    getAllergies = true;

                    if (param.getPart().isEmpty()) {
//                       addWarningIssue(param, IssueType.REQUIRED, "Miss parameter part : " + SystemConstants.INCLUDE_RESOLVED_ALLERGIES);
                        throw OperationOutcomeFactory.buildOperationOutcomeException(
                                new UnprocessableEntityException("Miss parameter : " + SystemConstants.INCLUDE_RESOLVED_ALLERGIES),
                                SystemCode.INVALID_PARAMETER, IssueType.REQUIRED);
                    }

                    boolean includeResolvedParameterPartPresent = false;
                    for (ParametersParameterComponent paramPart : param.getPart()) {
                        if (paramPart.getName().equals(SystemConstants.INCLUDE_RESOLVED_ALLERGIES)) {
                            if (paramPart.getValue() instanceof BooleanType) {
                                includeResolved = Boolean.valueOf(paramPart.getValue().primitiveValue());
                                includeResolvedParameterPartPresent = true;
                            } else {
                                throw OperationOutcomeFactory.buildOperationOutcomeException(
                                        new UnprocessableEntityException("Miss parameter : " + SystemConstants.INCLUDE_RESOLVED_ALLERGIES),
                                        SystemCode.INVALID_PARAMETER, IssueType.REQUIRED);

                            }
                        } else {
                            addWarningIssue(param, paramPart, IssueType.NOTSUPPORTED);
//                            throw OperationOutcomeFactory.buildOperationOutcomeException(
//                                    new UnprocessableEntityException("Incorrect parameter passed : " + paramPart.getName()),
//                                    SystemCode.INVALID_PARAMETER, IssueType.INVALID);
                        }
                    }
                    if (!includeResolvedParameterPartPresent) {
                        throw OperationOutcomeFactory.buildOperationOutcomeException(
                                new UnprocessableEntityException("Miss parameter : " + SystemConstants.INCLUDE_RESOLVED_ALLERGIES),
                                SystemCode.INVALID_PARAMETER, IssueType.REQUIRED);
                    }
                }

                if (param.getName().equals(SystemConstants.INCLUDE_MEDICATION)) {
                    getMedications = true;

                    boolean isIncludedPrescriptionIssuesExist = false;
                    for (ParametersParameterComponent paramPart : param.getPart()) {

                        if (paramPart.getName().equals(SystemConstants.INCLUDE_PRESCRIPTION_ISSUES)) {
                            if (paramPart.getValue() instanceof BooleanType) {
                                includePrescriptionIssues = Boolean.valueOf(paramPart.getValue().primitiveValue());
                                isIncludedPrescriptionIssuesExist = true;
                            }
                        } else if (paramPart.getName().equals(SystemConstants.MEDICATION_SEARCH_FROM_DATE)
                                && paramPart.getValue() instanceof DateType) {
                            DateType startDateDt = (DateType) paramPart.getValue();
                            medicationPeriod = new Period();
                            medicationPeriod.setStart(startDateDt.getValue());
                            medicationPeriod.setEnd(null);
                            String startDate = startDateDt.asStringValue();
                            if (!validateStartDateParamAndEndDateParam(startDate, null)) {
                                //addWarningIssue(param, paramPart, IssueType.INVALID, "Invalid date used");
                            }
                        } else {
                            addWarningIssue(param, paramPart, IssueType.NOTSUPPORTED);
//                            throw OperationOutcomeFactory.buildOperationOutcomeException(
//                                    new UnprocessableEntityException("Incorrect parameter passed : " + paramPart.getName()),
//                                    SystemCode.INVALID_PARAMETER, IssueType.INVALID);
                        }
                    }

                    if (!isIncludedPrescriptionIssuesExist) {
                        // # 1.2.6 now defaults to true if not provided
                        includePrescriptionIssues = true;
                    }
                }
            } else {
                // invalid parameter
                addWarningIssue(param, IssueType.NOTSUPPORTED);
            }
        } // for parameter

        // Add Patient
        Patient patient = patientDetailsToPatientResourceConverter(patientDetails);
        if (patient.getIdentifierFirstRep().getValue().equals(NHS)) {
            structuredBundle.addEntry().setResource(patient);
        }

        //Organization from patient
        Set<String> orgIds = new HashSet<>();
        orgIds.add(patientDetails.getManagingOrganization());

        //Practitioner from patient
        Set<String> practitionerIds = new HashSet<>();
        List<Reference> practitionerReferenceList = patient.getGeneralPractitioner();
        practitionerReferenceList.forEach(practitionerReference -> {
            String[] pracRef = practitionerReference.getReference().split("/");
            if (pracRef.length > 1) {
                practitionerIds.add(pracRef[1]);
            }
        });

        if (getAllergies) {
            structuredBundle = structuredAllergyIntoleranceBuilder.buildStructuredAllergyIntolerence(NHS, practitionerIds, structuredBundle, includeResolved);
        }
        if (getMedications) {
            structuredBundle = populateMedicationBundle.addMedicationBundleEntries(structuredBundle, patientDetails, includePrescriptionIssues, medicationPeriod, practitionerIds, orgIds);
        }

        //Add all practitioners and practitioner roles
        for (String practitionerId : practitionerIds) {
            Practitioner pracResource = practitionerResourceProvider.getPractitionerById(new IdType(practitionerId));
            structuredBundle.addEntry().setResource(pracResource);

            List<PractitionerRole> practitionerRoleList = practitionerRoleResourceProvider.getPractitionerRoleByPracticionerId(new IdType(practitionerId));
            for (PractitionerRole role : practitionerRoleList) {
                String[] split = role.getOrganization().getReference().split("/");
                orgIds.add(split[1]);
                structuredBundle.addEntry().setResource(role);
            }
        }

        //Add all organizations
        for (String orgId : orgIds) {
            OrganizationDetails organizationDetails = organizationSearch.findOrganizationDetails(new Long(orgId));
            Organization organization = organizationResourceProvider.convertOrganizationDetailsToOrganization(organizationDetails);
            structuredBundle.addEntry().setResource(organization);
        }

        structuredBundle.setType(BundleType.COLLECTION);
        structuredBundle.getMeta().addProfile(SystemURL.SD_GPC_STRUCTURED_BUNDLE);

        if (operationOutcome != null) {
            structuredBundle.addEntry().setResource(operationOutcome);
        } else {
            removeDuplicateResources(structuredBundle);
        }
        return structuredBundle;
    }

    /**
     *
     * @return new Object
     */
    private void createOperationOutcome() {
        operationOutcome = new OperationOutcome();
        // TODO Check this it doesn't look consistent but its as per the example
        operationOutcome.setId(java.util.UUID.randomUUID().toString());
        operationOutcome.getMeta().addProfile(SystemURL.SD_GPC_OPERATIONOUTCOME);
    }

    /**
     * Overload
     *
     * @param param
     * @param paramPart
     * @param issueType
     */
    private void addWarningIssue(ParametersParameterComponent param, ParametersParameterComponent paramPart, IssueType issueType) {
        addWarningIssue(param, paramPart, issueType, null);
    }

    /**
     * Overload
     *
     * @param param
     * @param issueType
     */
    private void addWarningIssue(ParametersParameterComponent param, IssueType issueType) {
        addWarningIssue(param, null, issueType, null);
    }

    /**
     * Overload
     *
     * @param param
     * @param issueType
     * @param details
     */
    private void addWarningIssue(ParametersParameterComponent param, IssueType issueType, String details) {
        addWarningIssue(param, null, issueType, details);
    }

    /**
     * see
     * https://gpconnect-1-2-4.netlify.com/accessrecord_structured_development_version_compatibility.html
     * add an issue to the OperationOutcome to be returned in a successful
     * response bundle this is for forward compatibility as specified in 1.2.4
     *
     * @param param
     * @param paramPart
     * @param issueType
     * @param details lower level details to be added to the text element
     */
    private void addWarningIssue(ParametersParameterComponent param, ParametersParameterComponent paramPart, IssueType issueType, String details) {
        if (operationOutcome == null) {
            createOperationOutcome();
        }
        OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
        issue.setSeverity(OperationOutcome.IssueSeverity.WARNING);

        CodeableConcept codeableConcept = new CodeableConcept();
        Coding coding = new Coding();
        coding.setSystem(VS_GPC_ERROR_WARNING_CODE);
        switch (issueType) {
            case NOTSUPPORTED:
                issue.setCode(issueType);
                coding.setCode(SystemCode.NOT_IMPLEMENTED);
                coding.setDisplay("Not implemented");
                break;
            case REQUIRED:
                issue.setCode(issueType);
                coding.setCode(SystemCode.PARAMETER_NOT_FOUND);
                coding.setDisplay("Parameter not found");
                break;
            case INVALID:
                issue.setCode(issueType);
                coding.setCode(SystemCode.INVALID_PARAMETER);
                coding.setDisplay("Invalid Parameter");
                break;
        }

        codeableConcept.addCoding(coding);
        issue.setDetails(codeableConcept);

        String locus = paramPart != null ? "." + paramPart.getName() : "";
        issue.setDiagnostics(param.getName() + locus);
        if (details == null) {
            // mod to remove more informative text which was off spec
            codeableConcept.setText(param.getName() + locus + " is an unrecognised parameter" /*+ (paramPart != null ? " part" : "")*/);
        } else {
            codeableConcept.setText(details);
        }
        operationOutcome.addIssue(issue);
    }

    private boolean validateParametersName(String name) {
        boolean result = true;
        if (!name.equals(SystemConstants.PATIENT_NHS_NUMBER) && !name.equals(SystemConstants.INCLUDE_ALLERGIES) && !name.equals(SystemConstants.INCLUDE_MEDICATION)) {
            result = false;
//            throw OperationOutcomeFactory.buildOperationOutcomeException(
//                    new InvalidRequestException("Incorrect Paramater Names"), SystemCode.INVALID_PARAMETER,
//                    IssueType.INVALID);
        }
        return result;
    }

    @Operation(name = REGISTER_PATIENT_OPERATION_NAME)
    public Bundle registerPatient(@ResourceParam Parameters params) {
        Patient registeredPatient = null;

        validateParameterNames(params, registerPatientParams);

        Patient unregisteredPatient = params.getParameter().stream()
                .filter(param -> "registerPatient".equalsIgnoreCase(param.getName()))
                .map(ParametersParameterComponent::getResource).map(Patient.class::cast).findFirst().orElse(null);

        String nnn = nhsNumber.fromPatientResource(unregisteredPatient);

        // see https://nhsconnect.github.io/gpconnect/foundations_use_case_register_a_patient.html#error-handling
        // if its patient 14 spoof not on PDS and return the required error 
        if (nnn.equals(patientNotOnSpine)) {
            throw OperationOutcomeFactory.buildOperationOutcomeException(
                    new InvalidRequestException(String.format("Patient (NHS number - %s) not present on PDS", nnn)),
                    SystemCode.INVALID_PATIENT_DEMOGRAPHICS, IssueType.INVALID);
        } else if (nnn.equals(patientSuperseded)) {
            throw OperationOutcomeFactory.buildOperationOutcomeException(
                    new InvalidRequestException(String.format("Patient (NHS number - %s) is superseded", nnn)),
                    SystemCode.INVALID_NHS_NUMBER, IssueType.INVALID);
        }

        if (unregisteredPatient != null) {
            validatePatient(unregisteredPatient);

            // check if the patient already exists
            PatientDetails patientDetails = patientSearch
                    .findPatient(nhsNumber.fromPatientResource(unregisteredPatient));

            if (patientDetails == null || IsInactiveTemporaryPatient(patientDetails)) {

                if (patientDetails == null) {
                    patientDetails = registerPatientResourceConverterToPatientDetail(unregisteredPatient);

                    patientStore.create(patientDetails);
                } else {
                    // reactivate inactive non temporary patient
                    patientDetails.setRegistrationStatus(ACTIVE_REGISTRATION_STATUS);
                    updateAddressAndTelecom(unregisteredPatient, patientDetails);

                    patientStore.update(patientDetails);
                }
                try {
                    registeredPatient = patientDetailsToRegisterPatientResourceConverter(
                            patientSearch.findPatient(unregisteredPatient.getIdentifierFirstRep().getValue()));

                    addPreferredBranchSurgeryExtension(registeredPatient);
                } catch (FHIRException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } else if (patientDetails.isDeceased() || patientDetails.isSensitive()) {
                throw OperationOutcomeFactory.buildOperationOutcomeException(
                        new InvalidRequestException(String.format("Patient (NHS number - %s) has invalid demographics", nnn)),
                        SystemCode.INVALID_PATIENT_DEMOGRAPHICS, IssueType.INVALID);

            } else {
                throw OperationOutcomeFactory.buildOperationOutcomeException(
                        new UnclassifiedServerFailureException(409, String.format("Patient (NHS number - %s) already exists", nnn)),
                        SystemCode.DUPLICATE_REJECTED, IssueType.INVALID);
            }
        } else {
            throw OperationOutcomeFactory.buildOperationOutcomeException(
                    new UnprocessableEntityException("Patient record not found"), SystemCode.INVALID_PARAMETER,
                    IssueType.INVALID);
        }

        Bundle bundle = new Bundle().setType(BundleType.SEARCHSET);
        bundle.getMeta().addProfile(SystemURL.SD_GPC_SRCHSET_BUNDLE);
        bundle.addEntry().setResource(registeredPatient);

        return bundle;
    }

    private void updateAddressAndTelecom(Patient unregisteredPatient, PatientDetails patientDetails) {
        ArrayList<TelecomDetails> al = new ArrayList<>();
        if (unregisteredPatient.getTelecom().size() > 0) {
            for (ContactPoint contactPoint : unregisteredPatient.getTelecom()) {
                TelecomDetails telecomDetails = new TelecomDetails();
                telecomDetails.setSystem(contactPoint.getSystem().toString());
                telecomDetails.setUseType(contactPoint.getUse().toString());
                telecomDetails.setValue(contactPoint.getValue());
                al.add(telecomDetails);
            }
        }
        patientDetails.setTelecoms(al);

        // actually a list of addresses not a single one
        if (unregisteredPatient.getAddress().size() > 0) {
            // get the first one off the block
            Address address = unregisteredPatient.getAddress().get(0);
            String[] addressLines = new String[ADDRESS_DISTRICT_INDEX + 1];
            List<StringType> addressLineList = address.getLine();
            for (int i = 0; i < ADDRESS_CITY_INDEX; i++) {
                if (i < addressLineList.size()) {
                    addressLines[i] = addressLineList.get(i).asStringValue();
                } else {
                    addressLines[i] = null;
                }
            }
            addressLines[ADDRESS_CITY_INDEX] = address.getCity();
            addressLines[ADDRESS_DISTRICT_INDEX] = address.getDistrict();
            patientDetails.setAddress(addressLines);
            patientDetails.setPostcode(address.getPostalCode());
        }
    }

    /**
     * Returns true if registration type is temporary AND the record is marked
     * inactive
     *
     * @param patientDetails assumed non null
     * @return Boolean object
     */
    private Boolean IsInactiveTemporaryPatient(PatientDetails patientDetails) {

        return patientDetails.getRegistrationType() != null
                && TEMPORARY_RESIDENT_REGISTRATION_TYPE.equals(patientDetails.getRegistrationType())
                && patientDetails.getRegistrationStatus() != null
                && ACTIVE_REGISTRATION_STATUS.equals(patientDetails.getRegistrationStatus()) == false;
    }

    public String getNhsNumber(Object source) {
        return nhsNumber.getNhsNumber(source);
    }

    private void validatePatient(Patient patient) {
        validateIdentifiers(patient);
        validateTelecomAndAddress(patient);
        validateConstrainedOutProperties(patient);
        checkValidExtensions(patient.getExtension());
        validateNames(patient);
        validateDateOfBirth(patient);
        validateGender(patient);
    }

    private void validateTelecomAndAddress(Patient patient) {
        // 0..1 of phone - (not nec. temp),  0..1 of email
        HashSet<ContactPointUse> phoneUse = new HashSet<>();
        int emailCount = 0;
        for (ContactPoint telecom : patient.getTelecom()) {
            if (telecom.hasSystem()) {
                if (telecom.getSystem() != null) {
                    switch (telecom.getSystem()) {
                        case PHONE:
                            if (telecom.hasUse()) {
                                switch (telecom.getUse()) {
                                    case HOME:
                                    case WORK:
                                    case MOBILE:
                                    case TEMP:
                                        if (!phoneUse.contains(telecom.getUse())) {
                                            phoneUse.add(telecom.getUse());
                                        } else {
                                            throwInvalidRequest400_BadRequestException("Only one Telecom of type phone with use type "
                                                    + telecom.getUse().toString().toLowerCase() + " is allowed in a register patient request.");
                                        }
                                        break;
                                    default:
                                        throwInvalidRequest400_BadRequestException(
                                                "Invalid Telecom of type phone use type " + telecom.getUse().toString().toLowerCase()
                                                + " in a register patient request.");
                                }
                            } else {
                                throwInvalidRequest400_BadRequestException("Invalid Telecom - no Use type provided in a register patient request.");
                            }
                            break;
                        case EMAIL:
                            if (++emailCount > 1) {
                                throwInvalidRequest400_BadRequestException("Only one Telecom of type " + "email" + " is allowed in a register patient request.");
                            }
                            break;
                        default:
                            throwInvalidRequest400_BadRequestException("Telecom system is missing in a register patient request.");
                    }
                }
            } else {
                throwInvalidRequest400_BadRequestException("Telecom system is missing in a register patient request.");
            }
        } // iterate telcom 

        // count by useType - Only the first address is persisted at present
        HashSet<AddressUse> useTypeCount = new HashSet<>();
        for (Address address : patient.getAddress()) {
            AddressUse useType = address.getUse();
            // #189 address use types work and old are not allowed
            if (useType == WORK || useType == OLD) {
                throwUnprocessableEntity422_InvalidResourceException("Address use type " + useType + " cannot be sent in a register patient request.");
            }
            if (!useTypeCount.contains(useType)) {
                useTypeCount.add(useType);
            } else {
                // #174 Only a single address of each usetype may be sent
                throwUnprocessableEntity422_InvalidResourceException("Only a single address of each use type can be sent in a register patient request.");
            }
        } // for address
    } //  validateTelecomAndAddress

    private void validateGender(Patient patient) {
        AdministrativeGender gender = patient.getGender();

        if (gender != null) {

            EnumSet<AdministrativeGender> genderList = EnumSet.allOf(AdministrativeGender.class);
            Boolean valid = false;
            for (AdministrativeGender genderItem : genderList) {

                if (genderItem.toCode().equalsIgnoreCase(gender.toString())) {
                    valid = true;
                    break;
                }
            }

            if (!valid) {
                throwInvalidRequest400_BadRequestException(String.format("The supplied Patient gender %s is an unrecognised type.", gender));
            }
        }
    }

    private void validateDateOfBirth(Patient patient) {
        Date birthDate = patient.getBirthDate();

        if (birthDate == null) {
            throwInvalidRequest400_BadRequestException("The Patient date of birth must be supplied");
        }
    }

    private void validateIdentifiers(Patient patient) {
        List<Identifier> identifiers = patient.getIdentifier();
        if (identifiers.isEmpty() == false) {
            boolean identifiersValid = identifiers.stream()
                    .allMatch(identifier -> identifier.getSystem() != null && identifier.getValue() != null);

            if (identifiersValid == false) {
                throwInvalidRequest400_BadRequestException("One or both of the system and/or value on some of the provided identifiers is null");
            }
        } else {
            throwInvalidRequest400_BadRequestException("At least one identifier must be supplied on a Patient resource");
        }
    }

    private void checkValidExtensions(List<Extension> undeclaredExtensions) {

        // This list must be empty for the request to be valid
        List<String> extensionURLs = undeclaredExtensions.stream().map(Extension::getUrl).collect(Collectors.toList());

        // see https://nhsconnect.github.io/gpconnect/foundations_use_case_register_a_patient.html
        extensionURLs.remove(SystemURL.SD_EXTENSION_CC_REG_DETAILS);

        // these commented out enties are not allowed at 1.2.2 so don't get removed from the list
        //extensionURLs.remove(SystemURL.SD_CC_EXT_ETHNIC_CATEGORY);
        //extensionURLs.remove(SystemURL.SD_CC_EXT_RELIGIOUS_AFFILI);
        //extensionURLs.remove(SystemURL.SD_PATIENT_CADAVERIC_DON);
        //extensionURLs.remove(SystemURL.SD_CC_EXT_RESIDENTIAL_STATUS);
        //extensionURLs.remove(SystemURL.SD_CC_EXT_TREATMENT_CAT);
        //extensionURLs.remove(SystemURL.SD_CC_EXT_NHS_COMMUNICATION);
        // 1.2.2 allows 0..1 SD_CC_EXT_NHS_COMMUNICATION
        long communicationCount = extensionURLs.stream().filter(extension -> extension.equals(SD_CC_EXT_NHS_COMMUNICATION)).count();
        if (communicationCount == 1) {
            extensionURLs.remove(SystemURL.SD_CC_EXT_NHS_COMMUNICATION);
            // also remove any associated "interpreterRequired" url, other urls from eg language etc dont appear in this list.
            // so don't need to be removed
            extensionURLs.remove(SystemURL.SD_CC_INTERPRETER_REQUIRED);
        }

        if (!extensionURLs.isEmpty()) {
            throwUnprocessableEntity422_InvalidResourceException("Invalid/multiple patient extensions found. The following are in excess or invalid: "
                    + extensionURLs.stream().collect(Collectors.joining(", ")));
        }
    }

    private void validateConstrainedOutProperties(Patient patient) {

        Set<String> invalidFields = new HashSet<>();

        // ## The above can exist in the patient resource but can be ignored. If
        // they are saved by the provider then they should be returned in the
        // response!
        if (patient.getPhoto().isEmpty() == false) {
            invalidFields.add("photo");
        }
        if (patient.getAnimal().isEmpty() == false) {
            invalidFields.add("animal");
        }
        if (patient.getCommunication().isEmpty() == false) {
            invalidFields.add("communication");
        }
        if (patient.getLink().isEmpty() == false) {
            invalidFields.add("link");
        }
        if (patient.getDeceased() != null) {
            invalidFields.add("deceased");
        }
        // 6 extra fields added at 1.2.2 
        if (patient.hasActive()) {
            invalidFields.add("active");
        }
        if (patient.hasMaritalStatus()) {
            invalidFields.add("maritalStatus");
        }
        if (patient.hasMultipleBirth()) {
            invalidFields.add("multipleBirths");
        }
        if (patient.hasContact()) {
            invalidFields.add("contact");
        }
        if (patient.hasManagingOrganization()) {
            invalidFields.add("mangingOrganization");
        }
        if (patient.hasGeneralPractitioner()) {
            invalidFields.add("generalPractitioner");
        }

        if (invalidFields.isEmpty() == false) {
            String message = String.format(
                    "The following properties have been constrained out on the Patient resource - %s",
                    String.join(", ", invalidFields));
            // #250 422 INVALID_RESOURCE not 400 BAD_REQUEST
            throw OperationOutcomeFactory.buildOperationOutcomeException(new UnprocessableEntityException(message),
                    SystemCode.INVALID_RESOURCE, IssueType.INVALID);
        }
    }

    private void validateNames(Patient patient) {
        List<HumanName> names = patient.getName();

        if (names.size() < 1) {
            throwInvalidRequest400_BadRequestException("The patient must have at least one Name.");
        }

        List<HumanName> activeOfficialNames = names
                .stream()
                .filter(nm -> IsActiveName(nm))
                .filter(nm -> NameUse.OFFICIAL.equals(nm.getUse()))
                .collect(Collectors.toList());

        if (activeOfficialNames.size() != 1) {
            throwInvalidRequest400_BadRequestException("The patient must have one Active Name with a Use of OFFICIAL");
        }

        List<String> officialFamilyNames = new ArrayList<>();

        for (HumanName humanName : activeOfficialNames) {
            if (humanName.getFamily() != null) {
                officialFamilyNames.add(humanName.getFamily());
            }
        }

        validateNameCount(officialFamilyNames, "family");
    }

    private void validateNameCount(List<String> names, String nameType) {
        if (names.size() != 1) {
            throwInvalidRequest400_BadRequestException(
                    String.format("The patient must have one and only one %s name property. Found %s",
                            nameType, names.size()));
        }
    }

    private Boolean IsActiveName(HumanName name) {

        Period period = name.getPeriod();

        if (null == period) {
            return true;
        }

        Date start = period.getStart();
        Date end = period.getEnd();

        return (null == end || end.after(new Date()))
                && (null == start || start.equals(new Date()) || start.before(new Date()));
    }

    private PatientDetails registerPatientResourceConverterToPatientDetail(Patient patientResource) {
        PatientDetails patientDetails = new PatientDetails();
        HumanName name = patientResource.getNameFirstRep();

        String givenNames = name.getGiven().stream().map(n -> n.getValue()).collect(Collectors.joining(","));

        patientDetails.setForename(givenNames);

        patientDetails.setSurname(name.getFamily());
        patientDetails.setDateOfBirth(patientResource.getBirthDate());
        if (patientResource.getGender() != null) {
            patientDetails.setGender(patientResource.getGender().toString());
        }
        patientDetails.setNhsNumber(patientResource.getIdentifierFirstRep().getValue());

        DateTimeType deceased = (DateTimeType) patientResource.getDeceased();
        if (deceased != null) {
            try {
                patientDetails.setDeceased((deceased.getValue()));
            } catch (ClassCastException cce) {
                throwUnprocessableEntity422_InvalidResourceException("The multiple deceased property is expected to be a datetime");
            }
        }

        // activate patient as temporary
        patientDetails.setRegistrationStartDateTime(new Date());
        // patientDetails.setRegistrationEndDateTime(getRegistrationEndDate(patientResource));
        patientDetails.setRegistrationStatus(ACTIVE_REGISTRATION_STATUS);
        patientDetails.setRegistrationType(TEMPORARY_RESIDENT_REGISTRATION_TYPE);
        updateAddressAndTelecom(patientResource, patientDetails);

        // set some standard values for defaults, ensure managing org is always returned
        // added at 1.2.2 7 is A20047 the default GP Practice
        patientDetails.setManagingOrganization("7");

        return patientDetails;
    }

    /**
     * only used on register patient call
     *
     * @param patient Patient object
     * @return patient object adorned with "static" data
     */
    private Patient setStaticPatientData(Patient patient) {

        patient.setLanguage(("en-GB"));

        setStaticCommunicationData(patient);

        Identifier localIdentifier = new Identifier();
        localIdentifier.setUse(IdentifierUse.USUAL);
        localIdentifier.setSystem(SystemURL.ID_LOCAL_PATIENT_IDENTIFIER);
        localIdentifier.setValue("123456");

        CodeableConcept liType = new CodeableConcept();
        Coding liTypeCoding = new Coding();
        liTypeCoding.setCode("EN");
        liTypeCoding.setDisplay("Employer number");
        liTypeCoding.setSystem(SystemURL.VS_IDENTIFIER_TYPE);
        liType.addCoding(liTypeCoding);
        localIdentifier.setType(liType);

        localIdentifier.setAssigner(new Reference("Organization/1"));
        patient.addIdentifier(localIdentifier);

        Calendar calendar = Calendar.getInstance();
        calendar.set(2017, 1, 1);
        calendar.set(2016, 1, 1);
        Period pastPeriod = new Period().setStart(calendar.getTime()).setEnd(calendar.getTime());

        patient.addName()
                .setFamily("AnotherOfficialFamilyName")
                .addGiven("AnotherOfficialGivenName")
                .setUse(NameUse.OFFICIAL)
                .setPeriod(pastPeriod);

        patient.addName()
                .setFamily("AdditionalFamily")
                .addGiven("AdditionalGiven")
                .setUse(NameUse.TEMP);

        //patient.addTelecom(staticElHelper.getValidTelecom());
        // TODO This appears to return a useless address element, only populated with use and type
        patient.addAddress(staticElHelper.getValidAddress());

        return patient;
    }

    private void setStaticCommunicationData(Patient patient) {
        // inhibited at 1.2.2
        //        patient.addExtension(createCodingExtension("CG", "Greek Cypriot", SystemURL.CS_CC_ETHNIC_CATEGORY_STU3,
        //                SystemURL.SD_CC_EXT_ETHNIC_CATEGORY));
        //        patient.addExtension(createCodingExtension("SomeSnomedCode", "Some Snomed Code",
        //                SystemURL.CS_CC_RELIGIOUS_AFFILI, SystemURL.SD_CC_EXT_RELIGIOUS_AFFILI));
        //        patient.addExtension(createCodingExtension("H", "UK Resident", SystemURL.CS_CC_RESIDENTIAL_STATUS_STU3,
        //                SystemURL.SD_CC_EXT_RESIDENTIAL_STATUS));
        //        patient.addExtension(createCodingExtension("3", "To pay hotel fees only", SystemURL.CS_CC_TREATMENT_CAT_STU3,
        //                SystemURL.SD_CC_EXT_TREATMENT_CAT));
        Extension nhsCommExtension = new Extension();
        nhsCommExtension.setUrl(SystemURL.SD_CC_EXT_NHS_COMMUNICATION);
        nhsCommExtension.addExtension(
                createCodingExtension("en", "English", SystemURL.CS_CC_HUMAN_LANG_STU3, SystemURL.SD_CC_EXT_COMM_LANGUAGE));
        nhsCommExtension.addExtension(new Extension(SystemURL.SD_CC_COMM_PREFERRED, new BooleanType(false)));
        nhsCommExtension.addExtension(createCodingExtension("RWR", "Received written",
                SystemURL.CS_CC_LANG_ABILITY_MODE_STU3, SystemURL.SD_CC_MODE_OF_COMM));
        nhsCommExtension.addExtension(createCodingExtension("E", "Excellent", SystemURL.CS_CC_LANG_ABILITY_PROFI_STU3,
                SystemURL.SD_CC_COMM_PROFICIENCY));
        nhsCommExtension.addExtension(new Extension(SystemURL.SD_CC_INTERPRETER_REQUIRED, new BooleanType(false)));

        patient.addExtension(nhsCommExtension);
    }

    private Extension createCodingExtension(String code, String display, String vsSystem, String extSystem) {

        Extension ext = new Extension(extSystem, createCoding(code, display, vsSystem));

        return ext;
    }

    private CodeableConcept createCoding(String code, String display, String vsSystem) {

        Coding coding = new Coding();
        coding.setCode(code);
        coding.setDisplay(display);
        coding.setSystem(vsSystem);
        CodeableConcept concept = new CodeableConcept();
        concept.addCoding(coding);

        return concept;
    }

    // a cut-down Patient
    private Patient patientDetailsToRegisterPatientResourceConverter(PatientDetails patientDetails)
            throws FHIRException {
        Patient patient = patientDetailsToMinimalPatient(patientDetails);

        HumanName name = getPatientNameFromPatientDetails(patientDetails);

        patient.addName(name);

        addTelecoms(patientDetails, patient);

        patient = setStaticPatientData(patient);

        return patient;
    }

    /**
     * from details to patient
     *
     * @param patientDetails
     * @param patient fhir resource
     */
    private void addTelecoms(PatientDetails patientDetails, Patient patient) {
        for (TelecomDetails telecomDetails : patientDetails.getTelecoms()) {
            patient.addTelecom(populateTelecom(telecomDetails));
        }
    }

    private Patient patientDetailsToMinimalPatient(PatientDetails patientDetails) throws FHIRException {
        Patient patient = new Patient();

        Date lastUpdated = patientDetails.getLastUpdated() == null ? new Date() : patientDetails.getLastUpdated();

        String resourceId = String.valueOf(patientDetails.getId());
        String versionId = String.valueOf(lastUpdated.getTime());
        String resourceType = patient.getResourceType().toString();

        IdType id = new IdType(resourceType, resourceId, versionId);

        patient.setId(id);
        patient.getMeta().setVersionId(versionId);
        patient.getMeta().addProfile(SystemURL.SD_GPC_PATIENT);

        Identifier patientNhsNumber = new Identifier().setSystem(SystemURL.ID_NHS_NUMBER)
                .setValue(patientDetails.getNhsNumber());

        Extension extension = createCodingExtension("01", "Number present and verified",
                SystemURL.CS_CC_NHS_NUMBER_VERIF_STU3, SystemURL.SD_CC_EXT_NHS_NUMBER_VERIF);

        patientNhsNumber.addExtension(extension);

        patient.addIdentifier(patientNhsNumber);

        patient.setBirthDate(patientDetails.getDateOfBirth());

        String gender = patientDetails.getGender();
        if (gender != null) {
            patient.setGender(AdministrativeGender.fromCode(gender.toLowerCase(Locale.UK)));
        }

        Date registrationEndDateTime = patientDetails.getRegistrationEndDateTime();
        Date registrationStartDateTime = patientDetails.getRegistrationStartDateTime();

        Extension regDetailsExtension = new Extension(SystemURL.SD_EXTENSION_CC_REG_DETAILS);

        Period registrationPeriod = new Period().setStart(registrationStartDateTime);
        if (registrationEndDateTime != null) {
            registrationPeriod.setEnd(registrationEndDateTime);
        }

        Extension regPeriodExt = new Extension(SystemURL.SD_CC_EXT_REGISTRATION_PERIOD, registrationPeriod);
        regDetailsExtension.addExtension(regPeriodExt);

        String registrationStatusValue = patientDetails.getRegistrationStatus();
        patient.setActive(
                ACTIVE_REGISTRATION_STATUS.equals(registrationStatusValue) || null == registrationStatusValue);

        String registrationTypeValue = patientDetails.getRegistrationType();
        if (registrationTypeValue != null) {

            Coding regTypeCode = new Coding();
            regTypeCode.setCode(registrationTypeValue);
            regTypeCode.setDisplay("Temporary"); // Should always be Temporary
            regTypeCode.setSystem(SystemURL.CS_REGISTRATION_TYPE);
            CodeableConcept regTypeConcept = new CodeableConcept();
            regTypeConcept.addCoding(regTypeCode);

            Extension regTypeExt = new Extension(SystemURL.SD_CC_EXT_REGISTRATION_TYPE, regTypeConcept);
            regDetailsExtension.addExtension(regTypeExt);
        }

        patient.addExtension(regDetailsExtension);

        if (patientDetails.isDeceased()) {
            DateTimeType decesed = new DateTimeType(patientDetails.getDeceased());
            patient.setDeceased(decesed);
        }

        String managingOrganization = patientDetails.getManagingOrganization();
        if (managingOrganization != null) {
            patient.setManagingOrganization(new Reference("Organization/" + managingOrganization));
        }

        // for patient 2 add some contact details
        if (patientDetails.getNhsNumber().equals(patient2)) {
            createContact(patient);
        }

        return patient;
    } // patientDetailsToMinimalPatient

    /**
     * add a set of contact details into the patient record NB these are
     * Contacts (related people etc) not contactpoints (telecoms)
     *
     * @param patient fhirResource object
     */
    private void createContact(Patient patient) {

        // relationships
        Patient.ContactComponent contact = new ContactComponent();
        for (String relationship : new String[]{"Emergency contact", "Next of kin", "Daughter"}) {
            CodeableConcept crelationship = new CodeableConcept();
            crelationship.setText(relationship);
            contact.addRelationship(crelationship);
        }

        // contact address
        Address address = new Address();
        address.addLine("Trevelyan Square");
        address.addLine("Boar Ln");
        address.setPostalCode("LS1 6AE");
        address.setType(AddressType.PHYSICAL);
        address.setUse(AddressUse.HOME);
        contact.setAddress(address);

        // gender
        contact.setGender(AdministrativeGender.FEMALE);

        // telecom
        ContactPoint telecom = new ContactPoint();
        telecom.setSystem(ContactPointSystem.PHONE);
        telecom.setUse(ContactPointUse.MOBILE);
        telecom.setValue("07777123123");
        contact.addTelecom(telecom);

        // Name
        HumanName name = new HumanName();
        name.addGiven("Jane");
        name.setFamily("Jackson");
        List<StringType> prefixList = new ArrayList<>();
        prefixList.add(new StringType("Miss"));
        name.setPrefix(prefixList);
        name.setText("JACKSON Jane (Miss)");
        name.setUse(NameUse.OFFICIAL);
        contact.setName(name);

        patient.addContact(contact);
    }

    private Patient patientDetailsToPatientResourceConverter(PatientDetails patientDetails) throws FHIRException {
        Patient patient = patientDetailsToMinimalPatient(patientDetails);

        HumanName name = getPatientNameFromPatientDetails(patientDetails);

        patient.addName(name);

        // now returns structured address (not using text element) at 1.2.2
        ArrayList<StringType> addressLines = new ArrayList<>();
        for (int i = 0; i < min(ADDRESS_CITY_INDEX, patientDetails.getAddress().length); i++) {
            addressLines.add(new StringType(patientDetails.getAddress()[i]));
        }
        patient.addAddress().
                setUse(AddressUse.HOME).
                setType(AddressType.PHYSICAL).
                setLine(addressLines).
                setCity(patientDetails.getAddress().length > ADDRESS_CITY_INDEX ? patientDetails.getAddress()[ADDRESS_CITY_INDEX] : "").
                setDistrict(patientDetails.getAddress().length > ADDRESS_DISTRICT_INDEX ? patientDetails.getAddress()[ADDRESS_DISTRICT_INDEX] : "").
                setPostalCode(patientDetails.getPostcode());

        Long gpId = patientDetails.getGpId();
        if (gpId != null) {
            Practitioner prac = practitionerResourceProvider.getPractitionerById(new IdType(gpId));
//          HumanName practitionerName = prac.getNameFirstRep();

            Reference practitionerReference = new Reference("Practitioner/" + gpId);
            // #243 remove display from reference elements
//                    .setDisplay(practitionerName.getPrefix().get(0) + " " + practitionerName.getGivenAsSingleString() + " "
//                            + practitionerName.getFamily());
            List<Reference> ref = new ArrayList<>();
            ref.add(practitionerReference);
            patient.setGeneralPractitioner(ref);
        }

        String telephoneNumber = patientDetails.getTelephone();
        ArrayList<ContactPoint> al = new ArrayList<>();
        // defaults to home, this is from the slot in the patients table
        if (telephoneNumber != null) {
            ContactPoint telephone = new ContactPoint().
                    setSystem(ContactPointSystem.PHONE).
                    setValue(telephoneNumber).
                    setUse(ContactPointUse.HOME);

            al.add(telephone);
        }

        // tack on any from the telecoms table 1.2.2 structured
        if (patientDetails.getTelecoms() != null) {
            for (TelecomDetails telecomDetails : patientDetails.getTelecoms()) {
                al.add(populateTelecom(telecomDetails));
            }
        }
        patient.setTelecom(al);

        String managingOrganization = patientDetails.getManagingOrganization();

        if (managingOrganization != null) {
            patient.setManagingOrganization(new Reference("Organization/" + managingOrganization));
        }

        // # 163 add patient language etc
        setStaticCommunicationData(patient);

        return patient;
    } // patientDetailsToPatientResourceConverter

    private ContactPoint populateTelecom(TelecomDetails telecomDetails) {
        return new ContactPoint().
                setSystem(ContactPoint.ContactPointSystem.valueOf(telecomDetails.getSystem())).
                setUse(ContactPoint.ContactPointUse.valueOf(telecomDetails.getUseType())).
                setValue(telecomDetails.getValue());
    }

    private HumanName getPatientNameFromPatientDetails(PatientDetails patientDetails) {
        HumanName name = new HumanName();

        name.setText(patientDetails.getName()).setFamily(patientDetails.getSurname())
                .addPrefix(patientDetails.getTitle()).setUse(NameUse.OFFICIAL);

        List<String> givenNames = patientDetails.getForenames();

        givenNames.forEach((givenName) -> {
            name.addGiven(givenName);
        });

        return name;
    }

    /**
     * This is a temporary sticking plaster to ensure no duplicates are returned
     * It was spotted when patient 12 was found to be returning two Medication/2
     * resources.
     *
     * @param structuredBundle
     */
    private void removeDuplicateResources(Bundle structuredBundle) {
        // take a copy into an array so we are not accused of modifying a collection while iterating through it.
        Bundle.BundleEntryComponent[] entries = structuredBundle.getEntry().toArray(new Bundle.BundleEntryComponent[0]);
        HashSet<String> hs = new HashSet<>();
        for (Bundle.BundleEntryComponent entry : entries) {
            if (entry.getResource().getId() != null) {
                String reference = entry.getResource().getResourceType().toString() + "/" + entry.getResource().getId();
                if (!hs.contains(reference)) {
                    hs.add(reference);
                } else {
                    System.out.println("Removing duplicate entry " + reference);
                    structuredBundle.getEntry().remove(entry);
                }
            }
        }
    }

    private class NhsNumber {

        private NhsNumber() {
            super();
        }

        private String getNhsNumber(Object source) {
            String nhsNumber = fromIdDt(source);

            if (nhsNumber == null) {
                nhsNumber = fromToken(source);

                if (nhsNumber == null) {
                    nhsNumber = fromParameters(source);

                    if (nhsNumber == null) {
                        nhsNumber = fromIdentifier(source);
                    }
                }
            }

            if (nhsNumber != null && !NhsCodeValidator.nhsNumberValid(nhsNumber)) {
                throw OperationOutcomeFactory.buildOperationOutcomeException(
                        new InvalidRequestException("Invalid NHS number submitted: " + nhsNumber),
                        SystemCode.INVALID_NHS_NUMBER, IssueType.INVALID);
            }

            return nhsNumber;
        }

        private String fromIdentifier(Object source) {
            String nhsNumber = null;

            if (source instanceof Identifier) {
                Identifier Identifier = (Identifier) source;

                String identifierSystem = Identifier.getSystem();
                if (identifierSystem != null && SystemURL.ID_NHS_NUMBER.equals(identifierSystem)) {
                    nhsNumber = Identifier.getValue();
                } else {
                    String message = String.format(
                            "The given identifier system code (%s) does not match the expected code - %s",
                            identifierSystem, SystemURL.ID_NHS_NUMBER);

                    throw OperationOutcomeFactory.buildOperationOutcomeException(new InvalidRequestException(message),
                            SystemCode.INVALID_IDENTIFIER_SYSTEM, IssueType.INVALID);
                }
            }

            return nhsNumber;
        }

        private String fromIdDt(Object source) {
            String nhsNumber = null;

            if (source instanceof IdDt) {
                IdDt idDt = (IdDt) source;

                PatientDetails patientDetails = patientSearch.findPatientByInternalID(idDt.getIdPart());
                if (patientDetails != null) {
                    nhsNumber = patientDetails.getNhsNumber();
                }
            }

            return nhsNumber;
        }

        private String fromToken(Object source) {
            String nhsNumber = null;

            if (source instanceof TokenParam) {
                TokenParam tokenParam = (TokenParam) source;

                if (!SystemURL.ID_NHS_NUMBER.equals(tokenParam.getSystem())) {
                    throw OperationOutcomeFactory.buildOperationOutcomeException(
                            new InvalidRequestException("Invalid system code"), SystemCode.INVALID_PARAMETER,
                            IssueType.INVALID);
                }

                nhsNumber = tokenParam.getValue();
            }

            return nhsNumber;
        }

        private String fromParameters(Object source) {
            String nhsNumber = null;

            if (source instanceof Parameters) {
                Parameters parameters = (Parameters) source;
                List<ParametersParameterComponent> params = new ArrayList<>();
                params.addAll(parameters.getParameter());

                ParametersParameterComponent parameter = getParameterByName(params, "patientNHSNumber");
                if (parameter != null) {
                    nhsNumber = fromIdentifier(parameter.getValue());
                } else {
                    parameter = getParameterByName(parameters.getParameter(), "registerPatient");
                    if (parameter != null) {
                        nhsNumber = fromPatientResource(parameter.getResource());
                    } else {
                        // 1.2.4 now Http 422 Unprocessable Entity not Http 400 Invalid Request
                        throw OperationOutcomeFactory.buildOperationOutcomeException(new UnprocessableEntityException(
                                "Unable to read parameters. Expecting one of patientNHSNumber or registerPatient both of which are case-sensitive"),
                                SystemCode.INVALID_PARAMETER, IssueType.INVALID);
                    }
                }
            }

            return nhsNumber;
        }

        private String fromPatientResource(Object source) {
            String nhsNumber = null;

            if (source instanceof Patient) {
                Patient patient = (Patient) source;

                nhsNumber = patient.getIdentifierFirstRep().getValue();
            }

            return nhsNumber;
        }

        private ParametersParameterComponent getParameterByName(List<ParametersParameterComponent> parameters,
                String parameterName) {
            ParametersParameterComponent parameter = null;

            List<ParametersParameterComponent> filteredParameters = parameters.stream()
                    .filter(currentParameter -> parameterName.equals(currentParameter.getName()))
                    .collect(Collectors.toList());

            if (filteredParameters != null) {
                if (filteredParameters.size() == 1) {
                    parameter = filteredParameters.iterator().next();
                } else if (filteredParameters.size() > 1) {
                    throwInvalidRequest400_BadRequestException("The parameter " + parameterName + " cannot be set more than once");
                }
            }

            return parameter;
        }
    }

    /**
     * Checks that the dates are ok and that end is not earlier than start
     *
     * @param startDate
     * @param endDate
     * @return
     */
    private boolean validateStartDateParamAndEndDateParam(String startDate, String endDate) {
        Pattern dateOnlyPattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
        StringBuilder sb = new StringBuilder();

        boolean result = true;

        Date now = new Date();
        try {
            result = checkDate(startDate, "Start", dateOnlyPattern, sb, result, now);
            result = checkDate(endDate, "End", dateOnlyPattern, sb, result, now);
            if (result && startDate != null && endDate != null) {
                Date startDt = DATE_FORMAT.parse(startDate);
                Date endDt = DATE_FORMAT.parse(endDate);
                if (endDt.before(startDt)) {
                    sb.append(" End date ").append(endDate).append(" is earlier than start date ").append(startDate);
                    result = false;
                }
            }
        } catch (ParseException ex) {
            result = false;
        }
        if (!result) {
            throwInvalidParameterOperationalOutcome("Invalid date used " + sb.toString());
        }
        return result;
    }

    /**
     * checks that date is in the correct format and is not in the future
     *
     * @param date Date object
     * @param dateLabel "start" or "end"
     * @param dateOnlyPattern regex for valid date strings
     * @param sb StringBuilder to allow appending of additional info regarding
     * the nature of the failure
     * @param result boolean
     * @param now Date object
     * @return boolean true => ok
     * @throws ParseException
     */
    private boolean checkDate(String date, String dateLabel, Pattern dateOnlyPattern, StringBuilder sb, boolean result, Date now) throws ParseException {
        if (date != null) {
            if (!dateOnlyPattern.matcher(date).matches()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(" ").append(dateLabel).append(" date ").append(date).append(" does not match yyyy-mm-dd");
                result = false;
            } else {
                // extra check that date is not in the future
                Date d = DATE_FORMAT.parse(date);
                if (d.after(now)) {
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    sb.append(" ").append(dateLabel).append(" date ").append(date).append(" is in the future");
                    result = false;
                }
            }
        }
        if (!result) {
            throwInvalidParameterOperationalOutcome("Invalid date used " + sb.toString());
        }
        return result;
    }

    private void throwInvalidParameterOperationalOutcome(String error) {
        throw OperationOutcomeFactory.buildOperationOutcomeException(
                new UnprocessableEntityException(
                        error),
                SystemCode.INVALID_PARAMETER, IssueType.INVALID);
    }
}
