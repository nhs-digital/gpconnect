/*
 * Copyright 2015 Ripple OSI
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package uk.gov.hscic.patient.details;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.hscic.model.patient.PatientDetails;

@Service
@Transactional
public class PatientSearch {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PatientEntityToDetailsTransformer patientEntityToDetailsTransformer;

    @Autowired
    private PatientDetailsToEntityTransformer patientDetailsToEntityTransformer;

    public PatientDetails findPatient(final String patientNHSNumber) {
        final PatientEntity patient = patientRepository.findByNhsNumber(patientNHSNumber);

        return patient == null
                ? null
                : patientEntityToDetailsTransformer.transform(patient);
    }

    /**
     * @param rawInternalID
     * @return PatientDetails - that match the ID or null if no match could be found
     */
    public PatientDetails findPatientByInternalID(final String rawInternalID) {
        PatientDetails patientDetails;  
        
        try {
            Long internaId = Long.valueOf(rawInternalID);
            final PatientEntity patient = patientRepository.findById(internaId).get();
            
            patientDetails = patient == null
                                 ? null
                                 : patientEntityToDetailsTransformer.transform(patient);
        }
        catch(NumberFormatException nfe) {
            patientDetails = null;
        }
        
        return patientDetails;
    }

    public void updatePatient(final PatientDetails patientDetails) {
        PatientEntity patientEntity = patientDetailsToEntityTransformer.transform(patientDetails);
        patientRepository.save(patientEntity);
    }
}
