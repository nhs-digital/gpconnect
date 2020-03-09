package uk.gov.hscic.metadata;

import ca.uhn.fhir.context.FhirContext;
import java.sql.Date;
import java.time.LocalDate;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.dstu3.hapi.rest.server.ServerCapabilityStatementProvider;
import org.hl7.fhir.dstu3.model.CapabilityStatement;
import ca.uhn.fhir.rest.server.RestfulServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import static uk.gov.hscic.InteractionId.REST_READ_STRUCTURED_METADATA;
import static uk.gov.hscic.SystemHeader.SSP_INTERACTIONID;
import static uk.gov.hscic.SystemURL.OD_GPC_GET_STRUCTURED_RECORD;
import static uk.gov.hscic.SystemURL.OD_GPC_REGISTER_PATIENT;
import uk.gov.hscic.SystemVariable;
import uk.gov.hscic.common.filters.FhirRequestGenericIntercepter;
import static uk.gov.hscic.patient.PatientResourceProvider.GET_STRUCTURED_RECORD_OPERATION_NAME;
import static uk.gov.hscic.patient.PatientResourceProvider.REGISTER_PATIENT_OPERATION_NAME;

public class GpConnectServerCapabilityStatementProvider extends ServerCapabilityStatementProvider {

    public GpConnectServerCapabilityStatementProvider(RestfulServer theRestfulServer) {
        super(theRestfulServer);
    }

    @Override
    public CapabilityStatement getServerConformance(HttpServletRequest theRequest) {
        final boolean FROM_JSON = true;

        CapabilityStatement capabilityStatement = null;
        if (!FROM_JSON) {
            //Get the automatically generated statement
            capabilityStatement = super.getServerConformance(theRequest);

            // added at 1.2.2 force overwrite the default entries which are not correct
            for (CapabilityStatement.CapabilityStatementRestComponent st : capabilityStatement.getRest()) {
                for (CapabilityStatement.CapabilityStatementRestOperationComponent operation : st.getOperation()) {
                    String opPlusDollar = "$" + operation.getName();
                    switch (opPlusDollar) {
                        case REGISTER_PATIENT_OPERATION_NAME:
                            operation.getDefinition().setReference(OD_GPC_REGISTER_PATIENT);
                            break;
                        case GET_STRUCTURED_RECORD_OPERATION_NAME:
                            operation.getDefinition().setReference(OD_GPC_GET_STRUCTURED_RECORD);
                            break;
                    }
                }
            }

        } else {
            try {
                // 1.2.6 #316
                String interactionId = theRequest.getHeader(SSP_INTERACTIONID);
                String capabilityFile = null;
                switch (interactionId) {
                    case REST_READ_STRUCTURED_METADATA:
                        capabilityFile ="structured_capability.json";
                        break;
                    default:
                        capabilityFile ="capability.json";
                }
                // read a json capability file
                String capabilityJson = new String(Files.readAllBytes(Paths.get(FhirRequestGenericIntercepter.getConfigPath() + "/"+capabilityFile)));
                FhirContext ctx = FhirContext.forDstu3();
                capabilityStatement = (CapabilityStatement) ctx.newJsonParser().parseResource(capabilityJson);
            } catch (IOException ex) {
                return null;
            }
        }
        //And add additional required information
        capabilityStatement.setVersion(SystemVariable.VERSION);
        capabilityStatement.setDescription("This server implements the GP Connect API version " + SystemVariable.VERSION);
        capabilityStatement.setName("GP Connect");
        capabilityStatement.setCopyright("Copyright NHS Digital 2018");
        capabilityStatement.getSoftware().setReleaseDate(Date.valueOf(LocalDate.parse("2017-09-27")));
        return capabilityStatement;
    }
}
