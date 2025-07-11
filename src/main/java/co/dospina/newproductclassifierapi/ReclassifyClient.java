package co.dospina.newproductclassifierapi;

import com.google.api.gax.rpc.ResourceExhaustedException;
import com.google.api.gax.rpc.UnavailableException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class ReclassifyClient {

    private final ChatClient chatClient;
    private final QuestionAnswerAdvisor advisor;

    @Autowired
    public ReclassifyClient(JdbcTemplate jdbcTemplate) {
        Injector injector = Guice.createInjector(new EmbeddingsModule(jdbcTemplate));
        VectorStore vectorStore = injector.getInstance(VectorStore.class);
        VertexAiGeminiChatModel chatModel = injector.getInstance(VertexAiGeminiChatModel.class);

        advisor = QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(
                SearchRequest.builder()
                    .similarityThreshold(0.4)
                    .topK(10)
                    .filterExpression("level == 4")
                    .build()
            )
            .build();

        chatClient = ChatClient.builder(chatModel)
            .build();
    }

    @Retryable(
        retryFor = {
            UnavailableException.class,
            ResourceExhaustedException.class
        },
        backoff = @Backoff(delay = 15000, multiplier = 2)
    )
    public String reclassifyProduct(Product product) {
        ChatResponse response = chatClient.prompt()
            .advisors(advisor)
            .user(
                String.format(
                    """
                        <context>
                            You are a classifier that assigns product descriptions to a category in the taxonomy format A:B:C:D based on the product's current classification, short description, and long description.
                        </context>
                        <instruction>
                            Respond with exactly one of the category codes.
                            If no exact match is found, choose the closest matching category.
                            Do not add any extra text or explanation.
                        </instruction>
                        <example>
                            This product was classified in the past taxonomy as %s = %s.
                        </example>
                        <task>
                            <short-description>%s</short-description>
                            <long-description>%s</long-description>
                        </task>
                        """,
                    product.getCurrentClassification(),
                    product.getCurrentClassificationDesc(),
                    product.getShortDescription(),
                    product.getLongDescription().orElse("")
                )
            )
            .call()
            .chatResponse();

        String autoClassification = response.getResult()
            .getOutput()
            .getText()
            .replaceAll("[\\r\\n]", " ")
            .trim();

        System.out.printf("%-20s %-30s %-30s %-5s%n",
            product.getBrandCode() + ":" + product.getPartNumber(),
            product.getManualNewTaxonomy(),
            autoClassification,
            product.getManualNewTaxonomy().equals(autoClassification) ? "Yes" : "No"
        );

        return autoClassification;
    }

    @Recover
    public String recoverUnavailableException(UnavailableException e, Product product) {
        // Log the error
        System.err.printf("Failed to reclassify product %s:%s after retries. Error: %s%n",
            product.getBrandCode(), product.getPartNumber(), e.getMessage());

        // Return a fallback value
        return "A:B:C:D";
    }

    @Recover
    public String recoverResourceExhaustedException(ResourceExhaustedException e, Product product) {
        // Log the error
        System.err.printf("Failed to reclassify product %s:%s after retries. Error: %s%n",
            product.getBrandCode(), product.getPartNumber(), e.getMessage());

        // Return a fallback value
        return "A:B:C:D";
    }

}
