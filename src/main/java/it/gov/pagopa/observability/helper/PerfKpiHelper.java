package it.gov.pagopa.observability.helper;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;

public class PerfKpiHelper {

    public static ConnectionStringBuilder getConnectionStringBuilder() throws Exception {

        final String ADX_CLUSTER_URL = System.getenv( "ADX_CLUSTER_URL");
        final String AZURE_AD_CLIENT_ID = System.getenv( "AZURE_AD_CLIENT_ID");
        final String AZURE_AD_CLIENT_SECRET = System.getenv( "AZURE_AD_CLIENT_SECRET");
        final String AZURE_AD_TENANT_ID = System.getenv( "AZURE_AD_TENANT_ID");

        if (ADX_CLUSTER_URL == null || AZURE_AD_CLIENT_ID == null || AZURE_AD_CLIENT_SECRET == null || AZURE_AD_TENANT_ID == null) {
            throw new IllegalArgumentException("Environment variables for Azure Data Explorer credentials are not set.");
        }
    
        // Create connection string using Application ID & Secret
        ConnectionStringBuilder csb = ConnectionStringBuilder.createWithAadApplicationCredentials(
            ADX_CLUSTER_URL,
            AZURE_AD_CLIENT_ID,
            AZURE_AD_CLIENT_SECRET,
            AZURE_AD_TENANT_ID
        );

        return csb;
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