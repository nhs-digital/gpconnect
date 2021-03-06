package uk.gov.hscic.model.patient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.dstu3.model.Type;
import uk.gov.hscic.model.telecom.TelecomDetails;

public class PatientDetails {
    // changed at 1.2.2
    private String[] address;
    private Date dateOfBirth;
    private Date deceased;
  

    private String forename;
    private String gender;
    private String gpDetails;
    private Long gpId;
    private String id;
    private Date lastUpdated;
    private String managingOrganization;
    private String maritalStatus;
    private Type multipleBirth;
    private String name;
    private String nhsNumber;
    private String pasNumber;
    private Date registrationEndDateTime;
    private Date registrationStartDateTime;
    private String registrationStatus;
    private String registrationType;
    private String surname;  
    private String telephone;
    private String title;
    // added at 1.2.2
    private boolean sensitive;
    private String postcode;
    
    // added for 1.2.2 structured
    private ArrayList<TelecomDetails> telecoms;

    public String[] getAddress() {
        return address;
    }

    public Date getDateOfBirth() {
        return dateOfBirth;
    }

    public Date getDeceased() {
        return deceased;
    }

    public void setDeceased(Date deceased) {
        this.deceased = deceased;
    }

    public String getForename() {
        return forename;
    }
    
    public List<String> getForenames() {      
        return Arrays.asList(forename.split("\\s*,\\s*"));
    }

    public String getGender() {
        return gender;
    }

    public String getGpDetails() {
        return gpDetails;
    }

    public Long getGpId() {
        return gpId;
    }

    public String getId() {
        return id;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public String getManagingOrganization() {
        return managingOrganization;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public String getName() {
        return name;
    }

    public String getNhsNumber() {
        return nhsNumber;
    }

    public String getPasNumber() {
        return pasNumber;
    }

    public Date getRegistrationEndDateTime() {
		return registrationEndDateTime;
	}

    public Date getRegistrationStartDateTime() {
		return registrationStartDateTime;
	}

    public String getRegistrationStatus() {
		return registrationStatus;
	}

    public String getRegistrationType() {
		return registrationType;
	}

    public String getSurname() {
        return surname;
    }

    public String getTelephone() {
        return telephone;
    }

    public String getTitle() {
        return title;
    }

    public boolean isDeceased() {
		return this.deceased != null;
	}

    public boolean isSensitive() {
        return sensitive;
    }
    
    public boolean isActive() {
        return registrationStatus == null || registrationStatus.equals("A");
    }

    public Type isMultipleBirth() {
		return multipleBirth;
	}

    public String getPostcode() {
        return postcode;
    }

    public ArrayList<TelecomDetails> getTelecoms() {
        return telecoms;
    }

    public void setAddress(String[] address) {
        this.address = address;
    }

    public void setDateOfBirth(Date dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public void setForename(String forename) {
        this.forename = forename;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public void setGpDetails(String gpDetails) {
        this.gpDetails = gpDetails;
    }

    public void setGpId(Long gpId) {
        this.gpId = gpId;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

	public void setManagingOrganization(String managingOrganization) {
        this.managingOrganization = managingOrganization;
    }

	public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

	public void setMultipleBirth(Type string) {
		this.multipleBirth = string;
	}

	public void setName(String name) {
        this.name = name;
    }

	public void setNhsNumber(String nhsNumber) {
        this.nhsNumber = nhsNumber;
    }

	public void setPasNumber(String pasNumber) {
        this.pasNumber = pasNumber;
    }

	public void setRegistrationEndDateTime(Date registrationEndDateTime) {
		this.registrationEndDateTime = registrationEndDateTime;
	}

	public void setRegistrationStartDateTime(Date registrationStartDateTime) {
		this.registrationStartDateTime = registrationStartDateTime;
	}

	public void setRegistrationStatus(String registrationStatus) {
		this.registrationStatus = registrationStatus;
	}

	public void setRegistrationType(String registrationType) {
		this.registrationType = registrationType;
	}

	public void setSurname(String surname) {
        this.surname = surname;
    }

	public void setTelephone(String telephone) {
        this.telephone = telephone;
    }
	
	public void setTitle(String title) {
        this.title = title;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    public void setTelecoms(ArrayList<TelecomDetails> telecoms) {
        this.telecoms = telecoms;
    }


}
