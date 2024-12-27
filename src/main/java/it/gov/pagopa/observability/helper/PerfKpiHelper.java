package it.gov.pagopa.observability.helper;
import java.util.Optional;

import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;

public class PerfKpiHelper {

    public static ConnectionStringBuilder getConnectionStringBuilder() throws Exception {
        String environment = Optional.ofNullable(System.getenv("Environment")).orElse("Azure");
        String clusterUrl = System.getenv("ClusterUrl");

        if ("Local".equalsIgnoreCase(environment)) {
            // Usa Azure CLI
            var azureCliCredential = new AzureCliCredentialBuilder().build();
            String accessToken = azureCliCredential.getToken(new TokenRequestContext()
                    .addScopes(clusterUrl + "/.default"))
                    .block().getToken();
            return ConnectionStringBuilder.createWithAadAccessTokenAuthentication(clusterUrl, accessToken);
        } else {
            // Managed Identity
            var managedIdentityCredential = new DefaultAzureCredentialBuilder().build();
            String accessToken = managedIdentityCredential.getToken(new TokenRequestContext()
                    .addScopes(clusterUrl + "/.default"))
                    .block().getToken();
            return ConnectionStringBuilder.createWithAadAccessTokenAuthentication(clusterUrl, accessToken);
        }
    }
}