# pagoPA Functions template

Java template to create an Azure Function.

## Function examples
There is an example of a Http Trigger function.

---

## Run locally with Docker
`docker build -t pagopa-functions-template .`

`docker run -p 8999:80 pagopa-functions-template`

### Test
`curl http://localhost:8999/example`

## Run locally with Maven

`mvn clean package`

`mvn azure-functions:run`

### Test
`curl http://localhost:7071/example` 

---


## TODO
Once cloned the repo, you should:
- to deploy on standard Azure service:
  - rename `deploy-pipelines-standard.yml` to `deploy-pipelines.yml`
  - remove `helm` folder
- to deploy on Kubernetes:
  - rename `deploy-pipelines-aks.yml` to `deploy-pipelines.yml`
  - customize `helm` configuration
- configure the following GitHub action in `.github` folder: 
  - `deploy.yml`
  - `sonar_analysis.yml`

Configure the SonarCloud project :point_right: [guide](https://pagopa.atlassian.net/wiki/spaces/DEVOPS/pages/147193860/SonarCloud+experimental).