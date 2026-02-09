package com.fedex.automation.model.graphql;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GraphqlRequestBody {
    private String query;
    private Object variables;
}