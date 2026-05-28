ARG JAVA_VERSION=17
# This image additionally contains function core tools – useful when using custom extensions
FROM mcr.microsoft.com/azure-functions/java:4.0-java$JAVA_VERSION-build AS installer-env

COPY . /src/java-function-app
RUN cd /src/java-function-app && \
    mkdir -p /home/site/wwwroot && \
    mvn clean package -Dmaven.test.skip=true && \
    cd ./target/azure-functions/ && \
    cd $(ls -d */|head -n 1) && \
    cp -a . /home/site/wwwroot

# This image isn't ssh enabled
# Pinned to 4.1046.100-2 because 4.1048.200 breaks Java worker initialization (Function host is not running)
FROM mcr.microsoft.com/azure-functions/java:4.1046.100-2-java$JAVA_VERSION

ENV AzureWebJobsScriptRoot=/home/site/wwwroot \
    AzureFunctionsJobHost__Logging__Console__IsEnabled=true

COPY --from=installer-env ["/home/site/wwwroot", "/home/site/wwwroot"]