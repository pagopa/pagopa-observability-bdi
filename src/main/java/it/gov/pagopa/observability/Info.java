package it.gov.pagopa.observability;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import  it.gov.pagopa.observability.models.AppInfo;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Azure Functions with Azure Http trigger.
 */
public class Info {

	/**
	 * This function will be invoked when a Http Trigger occurs
	 * @return
	 */
	@FunctionName("Info")
	public HttpResponseMessage run (
			@HttpTrigger(name = "InfoTrigger",
			methods = {HttpMethod.GET},
			route = "info",
			authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {

		context.getLogger().log(Level.INFO, "Invoked health check HTTP trigger for pagopa-observability-bdi.");
		return request.createResponseBuilder(HttpStatus.OK)
				.header("Content-Type", "application/json")
				.body(getInfo(context.getLogger(), "/META-INF/maven/it.gov.pagopa.observability/observability-bdi/pom.properties"))
				.build();
	}

	public synchronized AppInfo getInfo(Logger logger, String path) {
		String version = null;
		String name = null;
		try {
			Properties properties = new Properties();
			InputStream inputStream = getClass().getResourceAsStream(path);
			if (inputStream != null) {
				properties.load(inputStream);
				version = properties.getProperty("version", null);
				name = properties.getProperty("artifactId", null);
			}
		} catch (Exception e) {
			logger.severe("Impossible to retrieve information from pom.properties file.");
		}
		return AppInfo.builder().version(version).environment("azure-fn").name(name).build();
	}
}
