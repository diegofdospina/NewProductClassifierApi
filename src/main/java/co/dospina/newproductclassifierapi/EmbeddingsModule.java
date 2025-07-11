package co.dospina.newproductclassifierapi;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

public class CategoryVectorStore {

    @Bean
    public VectorStore vectorStore(
        JdbcTemplate jdbcTemplate,
        VertexAiTextEmbeddingModel embeddingModel
    ) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(768)                    // Optional: defaults to model dimensions or 1536
            .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
            .indexType(HNSW)                     // Optional: defaults to HNSW
            .initializeSchema(true)              // Optional: defaults to false
            .schemaName("public")                // Optional: defaults to "public"
            .vectorTableName("category_new")     // Optional: defaults to "vector_store"
            .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
            .build();
    }

}
