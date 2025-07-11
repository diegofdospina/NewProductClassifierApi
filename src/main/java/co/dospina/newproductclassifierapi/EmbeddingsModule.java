package co.dospina.newproductclassifierapi;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.cloud.vertexai.VertexAI;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingModel;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingOptions;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingOptions.TaskType;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel.ChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.jdbc.core.JdbcTemplate;

public class EmbeddingsModule extends AbstractModule {

    private final JdbcTemplate jdbcTemplate;

    public EmbeddingsModule(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Provides
    public VectorStore vectorStore(
        VertexAiTextEmbeddingModel embeddingModel
    ) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(768)
            .distanceType(COSINE_DISTANCE)
            .indexType(HNSW)
            .schemaName("public")
            .vectorTableName("product_repository_vector_store")
            .maxDocumentBatchSize(1000)
            .build();
    }

    @Provides
    public VertexAiTextEmbeddingModel provideEmbeddingModel(
        GoogleCredentials googleCredentials
    ) throws IOException {
        VertexAiEmbeddingConnectionDetails connectionDetails =
            VertexAiEmbeddingConnectionDetails.builder()
                .projectId("numeric-habitat-462514-m5")
                .location("us-central1")
                .predictionServiceSettings(
                    PredictionServiceSettings.newBuilder()
                        .setEndpoint("us-central1-aiplatform.googleapis.com:443")
                        .setQuotaProjectId("numeric-habitat-462514-m5")
                        .setCredentialsProvider(FixedCredentialsProvider.create(googleCredentials))
                        .build()
                )
                .build();

        VertexAiTextEmbeddingOptions options = VertexAiTextEmbeddingOptions.builder()
            .model("text-embedding-005")
            .dimensions(768)
            .taskType(TaskType.CLASSIFICATION)
            .build();

        return new VertexAiTextEmbeddingModel(connectionDetails, options);
    }

    @Provides
    public GoogleCredentials provideCredentials() throws IOException {
        GoogleCredentials credentials = GoogleCredentials
            .fromStream(new FileInputStream(
                "/Users/dospina/IdeaProjects/NewProductClassifierApi/service-account.json"))
            .createScoped("https://www.googleapis.com/auth/cloud-platform");

        credentials.refreshIfExpired();

        return credentials;
    }

    @Provides
    public VertexAI provideVertexAI(GoogleCredentials credentials) {
        return new VertexAI.Builder()
            .setCredentials(credentials)
            .setProjectId("numeric-habitat-462514-m5")
            .setLocation("us-central1")
            .build();
    }

    @Provides
    public VertexAiGeminiChatModel provideChatModel(VertexAI vertexAI) {
        return VertexAiGeminiChatModel.builder()
            .vertexAI(vertexAI)
            .defaultOptions(
                VertexAiGeminiChatOptions.builder()
                    .model(ChatModel.GEMINI_2_0_FLASH)
                    .temperature(0.0)
                    .maxOutputTokens(50)
                    .topK(1)
                    .topP(1.0)
                    .build()
            )
            .build();
    }

}
