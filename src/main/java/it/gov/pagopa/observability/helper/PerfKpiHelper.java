package it.gov.pagopa.observability.helper;
import java.util.Optional;

import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;

public class PerfKpiHelper {

    public static ConnectionStringBuilder getConnectionStringBuilder() throws Exception {
        String environment = Optional.ofNullable(System.getenv("ENVIRONMENT")).orElse("Azure");
        String clusterUrl = System.getenv("ADX_CLUSTER_URL");

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

    public static String getKVSecret(String secretName) {
    
        String keyVaultUrl = System.getenv("KEYVAULT_URI");

        SecretClient secretClient = new SecretClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();

        String secretValue = secretClient.getSecret(secretName).getValue();
        return secretValue;
    }
}