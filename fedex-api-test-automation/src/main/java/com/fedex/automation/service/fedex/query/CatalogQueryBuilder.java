package com.fedex.automation.service.fedex.query;

import com.fedex.automation.model.graphql.GraphqlRequestBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CatalogQueryBuilder {

    private static final String PRODUCT_SEARCH_QUERY = """
            query productSearch($phrase: String!, $pageSize: Int!) {
                productSearch(
                    filter: [
                        { attribute: \"shared_catalogs\", in: [\"3\"] },
                        { attribute: \"is_pending_review\", in: [\"0\", \"2\", \"3\"] }
                    ],
                    phrase: $phrase,
                    page_size: $pageSize
                ) {
                    items {
                        product {
                            sku
                            name
                        }
                        productView {
                            attributes {
                                label
                                name
                                value
                            }
                        }
                    }
                }
            }
            """;

    private final int pageSize;

    public CatalogQueryBuilder(@Value("${catalog.search.page-size:20}") int pageSize) {
        this.pageSize = pageSize;
    }

    public GraphqlRequestBody buildProductSearchRequest(String phrase) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("phrase", phrase);
        variables.put("pageSize", pageSize);
        return new GraphqlRequestBody(PRODUCT_SEARCH_QUERY, variables);
    }
}

