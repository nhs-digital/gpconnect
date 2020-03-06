'use strict';

angular.module('gpConnect').factory('PatientService', function ($rootScope, $http, FhirEndpointLookup, $cacheFactory, fhirJWTFactory, ProviderRouting) {
    var findAllSummaries = function() {
        return $http.get(ProviderRouting.defaultPractice().apiEndpointURL + '/patients');
    };

    var getSummary = function(practiceOdsCode, patientId) {
    	 $rootScope.patientOdsCode = practiceOdsCode;
    	return FhirEndpointLookup.getEndpoint($rootScope.patientOdsCode, "urn:nhs:names:services:gpconnect:fhir:operation:gpc.getcarerecord").then(function(response) {
            var endpointLookupResult = response;

            return $http.post(
                    endpointLookupResult.restUrlPrefix + '/Patient/$gpc.getcarerecord',
                    '{"resourceType" : "Parameters","parameter" : [{"name" : "patientNHSNumber","valueIdentifier" : { "system": "http://fhir.nhs.net/Id/nhs-number", "value" : "' + patientId + '" }},{"name" : "recordSection","valueCodeableConcept" :{"coding" : [{"system":"http://fhir.nhs.net/ValueSet/gpconnect-record-section-1","code":"SUM","display":"Summary"}]}}]}',
                    {
                        headers: {
                            'Ssp-From': endpointLookupResult.fromASID,
                            'Ssp-To': endpointLookupResult.toASID,
                            'Ssp-InteractionID': "urn:nhs:names:services:gpconnect:fhir:operation:gpc.getcarerecord",
                            'Ssp-TraceID': fhirJWTFactory.guid(),
                            'Authorization': "Bearer " + fhirJWTFactory.getJWT("patient", "read", patientId),
                            'Accept': "application/json+fhir",
                            'Content-Type': "application/json+fhir"
                        }
                    }
            );
        });
    };

    return {
        findAllSummaries: findAllSummaries,
        getSummary: getSummary
    };
});