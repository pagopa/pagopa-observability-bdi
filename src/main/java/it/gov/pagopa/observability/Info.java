package it.gov.pagopa.observability;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import  it.gov.pagopa.observability.models.AppInfo;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
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

		Logger logger = context.getLogger();

		logger.info("Info - invoked health check HTTP trigger for pagopa-observability-bdi");
		return request.createResponseBuilder(HttpStatus.OK)
				.header("Content-Type", "application/json")
				.body(getInfo(logger, "/META-INF/maven/it.gov.pagopa.observability/bdi-observability-functions/pom.properties"))
				.build();
	}

	public synchronized AppInfo getInfo(Logger logger, String path) {
		String version = null;
		String name = null;
		String env = System.getenv("ENVIRONMENT");
		try {
			Properties properties = new Properties();
			InputStream inputStream = getClass().getResourceAsStream(path);
			if (inputStream != null) {
				properties.load(inputStream);
				version = properties.getProperty("version", null);
				name = properties.getProperty("artifactId", "bdi-observability-functions");
				logger.info(String.format("Info - version [%s] name [%s] environment [%s]", version, name, env));
			}
		} catch (Exception e) {
			logger.severe("Info - impossible to retrieve information from pom.properties file.");
		}
		return AppInfo.builder().version(version).environment(env).name(name).build();
	}
}
