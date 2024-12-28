package it.gov.pagopa.observability.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class PerfDataResponse {
    private String status;
    private String message;
}
