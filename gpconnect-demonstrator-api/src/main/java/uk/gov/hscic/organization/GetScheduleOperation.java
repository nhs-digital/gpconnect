package uk.gov.hscic.organization;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Bundle.Entry;
import ca.uhn.fhir.model.dstu2.resource.Location;
import ca.uhn.fhir.model.dstu2.resource.OperationOutcome;
import ca.uhn.fhir.model.dstu2.resource.Organization;
import ca.uhn.fhir.model.dstu2.resource.Practitioner;
import ca.uhn.fhir.model.dstu2.resource.Schedule;
import ca.uhn.fhir.model.dstu2.resource.Slot;
import ca.uhn.fhir.model.dstu2.valueset.IssueSeverityEnum;
import uk.gov.hscic.appointments.ScheduleResourceProvider;
import uk.gov.hscic.appointments.SlotResourceProvider;
import uk.gov.hscic.location.LocationResourceProvider;
import uk.gov.hscic.practitioner.PractitionerResourceProvider;

/**
 * A Plain Provider. The $getschedule operation is not tied to a specific resource.
 */
@Component
public class GetScheduleOperation {
	
	ApplicationContext applicationContext;
    
    @Autowired
    LocationResourceProvider locationResourceProvider;
    
    @Autowired
    PractitionerResourceProvider practitionerResourceProvider;
    
    @Autowired
    SlotResourceProvider slotResourceProvider;
    
    @Autowired
    OrganizationResourceProvider organizationResourceProvider;
    
    @Autowired
    ScheduleResourceProvider scheduleResourceProvider;

    @SuppressWarnings("deprecation")
	void populateBundle(Bundle bundle, OperationOutcome operationOutcome, String orgOdsCode, String siteOdsCode, String planningHorizonStart, String planningHorizonEnd) { 
        // organisation
        List<Organization> organizations = organizationResourceProvider.getOrganizations(orgOdsCode, siteOdsCode);
        if(organizations.isEmpty() == false) {
        	if(organizations.size() == 1) {
        		Organization organization = organizations.get(0);
        		
        		Entry organisationEntry = new Entry();
        		organisationEntry.setResource(organization);
        		organisationEntry.setFullUrl("Organisation/" + organization.getId());
        		bundle.addEntry(organisationEntry);
        	}
        	else {
            	String msg = String.format("Multiple (%d) organisations were found with the organisation ODS code %s and the site ODS code %s. Only 1 is expected.", organizations.size(), orgOdsCode, siteOdsCode);
            	operationOutcome.addIssue().setSeverity(IssueSeverityEnum.INFORMATION).setDetails(msg);
            	
            	Entry operationOutcomeEntry = new Entry();
            	operationOutcomeEntry.setResource(operationOutcome);
            	bundle.addEntry(operationOutcomeEntry);	
        	}
        }
        else {
        	String msg = String.format("No organisation could be found for the organisation ODS code %s and the site ODS code %s", orgOdsCode, siteOdsCode);
        	operationOutcome.addIssue().setSeverity(IssueSeverityEnum.INFORMATION).setDetails(msg);
        	
        	Entry operationOutcomeEntry = new Entry();
        	operationOutcomeEntry.setResource(operationOutcome);
        	bundle.addEntry(operationOutcomeEntry);	
        }
        
        // location
        Location location = locationResourceProvider.getBySiteOdsCode(siteOdsCode);
        Entry locationEntry = new Entry();
        locationEntry.setResource(location);
        locationEntry.setFullUrl("Location/" + location.getId());       
        bundle.addEntry(locationEntry);
        
        // schedules
        List<Schedule> schedules = scheduleResourceProvider.getSchedulesForLocationId(location.getId().getValue(), planningHorizonStart, planningHorizonEnd);
        if(schedules.isEmpty() == false) {
        	for(Schedule schedule : schedules) {
        		Entry scheduleEntry = new Entry();
        		scheduleEntry.setResource(schedule);
        		scheduleEntry.setFullUrl("Schedule/" + schedule.getId());
        		bundle.addEntry(scheduleEntry);
        		
        		// practitioners
        		List<ExtensionDt> practitionerExtensions = scheduleResourceProvider.getPractitionerReferences(schedule);
        		if(practitionerExtensions.isEmpty() == false) {
	        		for(ExtensionDt practionerExtension : practitionerExtensions) {
	        			ResourceReferenceDt practitionerRef = (ResourceReferenceDt) practionerExtension.getValue();
	        			Practitioner practitioner = practitionerResourceProvider.getPractitionerById(practitionerRef.getReferenceElement());
	        			
	        			Entry practionerEntry = new Entry();
	        			practionerEntry.setResource(practitioner);
	        			practionerEntry.setFullUrl("Practitioner/" + practitioner.getId());
	        			bundle.addEntry(practionerEntry);
	        		}
        		}
        		else {
                	String msg = String.format("No practitioners could be found for the schedule %s", schedule.getId().getElementSpecificId());
                	operationOutcome.addIssue().setSeverity(IssueSeverityEnum.INFORMATION).setDetails(msg);
                	
                	Entry operationOutcomeEntry = new Entry();
                	operationOutcomeEntry.setResource(operationOutcome);
                	bundle.addEntry(operationOutcomeEntry);
        		}
        		
        		// slots
        		List<Slot> slots = slotResourceProvider.getSlotsForScheduleId(schedule.getId().getIdPart(), planningHorizonStart, planningHorizonEnd);
        		if(slots.isEmpty() == false) {
        			for(Slot slot : slots) {
        				Entry slotEntry = new Entry();
        				slotEntry.setResource(slot);
        				slotEntry.setFullUrl("Slot/" + slot.getId());
        				bundle.addEntry(slotEntry);
        			}
        		}
        		else {
                	String msg = String.format("No slots could be found for the schedule %s within the specified planning horizon (start - %s end - %s)", schedule.getId().getElementSpecificId(), planningHorizonStart, planningHorizonEnd);
                	operationOutcome.addIssue().setSeverity(IssueSeverityEnum.INFORMATION).setDetails(msg);
                	
                	Entry operationOutcomeEntry = new Entry();
                	operationOutcomeEntry.setResource(operationOutcome);
                	bundle.addEntry(operationOutcomeEntry);
        		}
        		
        	}
        }
        else {
        	String msg = String.format("No schedules could be found for the location (site ODS code - %s) within the planning horizon (start - %s end - %s)", siteOdsCode, planningHorizonStart, planningHorizonEnd);
        	operationOutcome.addIssue().setSeverity(IssueSeverityEnum.INFORMATION).setDetails(msg);
        	
        	Entry operationOutcomeEntry = new Entry();
        	operationOutcomeEntry.setResource(operationOutcome);
        	bundle.addEntry(operationOutcomeEntry);
        }
	}
    
//	private void addOrganisation(List<Organization> organizations, Bundle bundle, OperationOutcome operationOutcome) {
//		if(organizations.isEmpty() == false) {
//        	if(organizations.size() == 1) {
//        		
//        	}
//        	else {
//            	String msg = String.format("Multiple (%d) organisations were found with the organisation ODS code %s and the site ODS code %s. Only 1 is expected.", organizations.size(), orgOdsCode, siteOdsCode);
//            	operationOutcome.addIssue().setSeverity(IssueSeverityEnum.INFORMATION).setDetails(msg);
//            	
//            	Entry operationOutcomeEntry = new Entry();
//            	operationOutcomeEntry.setResource(operationOutcome);
//            	bundle.addEntry(operationOutcomeEntry);	
//        	}
//        }
//        else {
//        	String msg = String.format("No organisation could be found for the organisation ODS code %s and the site ODS code %s", orgOdsCode, siteOdsCode);
//        	operationOutcome.addIssue().setSeverity(IssueSeverityEnum.INFORMATION).setDetails(msg);
//        	
//        	Entry operationOutcomeEntry = new Entry();
//        	operationOutcomeEntry.setResource(operationOutcome);
//        	bundle.addEntry(operationOutcomeEntry);	
//        }
//	}
}