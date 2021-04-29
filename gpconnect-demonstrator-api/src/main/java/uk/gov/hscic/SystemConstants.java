package uk.gov.hscic;

public final class SystemConstants {

    //Structured Allergies

    public static final String MEDICATION = "medication";
    public static final String ACTIVE = "active";
    
    public static final String ACTIVE_ALLERGIES_DISPLAY = "Allergies and adverse reactions";
    // #175
    public static final String ACTIVE_ALLERGIES_TITLE = "Allergies and adverse reactions";
    
    public static final String RESOLVED_ALLERGIES_DISPLAY = "Ended allergies";
    // #175
    public static final String RESOLVED_ALLERGIES_TIILE = "Ended allergies";

    public static final String RESOLVED = "resolved";
    public static final String NO_KNOWN = "no known";
    public static final String NO_CONTENT_RECORDED_DISPLAY = "No Content Recorded";

    public static final String SNOMED_URL = "http://snomed.info/sct";

	public static final String  NO_CONTENT = "noContent";
    public static final String NO_CONTENT_RECORDED = "no-content-recorded";
    public static final String INFORMATION_NOT_AVAILABLE = "Information not available";
    public static final String NO_INFORMATION_AVAILABLE = "No information available"; // required structured
    public static final String PATIENT_REFERENCE_URL = "Patient/";
    public static final String INCLUDE_RESOLVED_ALLERGIES = "includeResolvedAllergies";
    public static final String INCLUDE_ALLERGIES = "includeAllergies";
    public static final String PATIENT_NHS_NUMBER = "patientNHSNumber";

    public static final String INCLUDE_MEDICATION = "includeMedication";
    public static final String INCLUDE_PRESCRIPTION_ISSUES = "includePrescriptionIssues";
    // 1.2.1
    public static final String MEDICATION_DATE_PERIOD = "medicationDatePeriod";
    // 1.2.2
    public static final String MEDICATION_SEARCH_FROM_DATE = "medicationSearchFromDate";

    public static final String MEDICATION_LIST = "Medications and medical devices";
    
    // #266 Notes associated with warnings
    public static final String CONFIDENTIAL_ITEMS_NOTE = "Items excluded due to confidentiality and/or patient preferences.";
    public static final String DATA_IN_TRANSIT_NOTE = "Patient record transfer from previous GP practice not yet complete; information recorded before %s may be missing.";
    public static final String DATA_AWAITING_FILING_NOTE = "Patient data may be incomplete as there is data supplied by a third party awaiting review before becoming available.";
    
}
