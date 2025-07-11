package co.dospina.newproductclassifierapi;

import com.google.api.gax.rpc.UnavailableException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.stereotype.Component;

@Component
public class ReClassify {

    private final ChatClient chatClient;
    private final QuestionAnswerAdvisor advisor;

    @Autowired
    public ReClassify(JdbcTemplate jdbcTemplate) {

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

    public void run() throws IOException {
        FileInputStream file = new FileInputStream("/Users/dospina/Downloads/sample_data.xlsx");
        Workbook workbook = new XSSFWorkbook(file);

        Sheet sheet = workbook.getSheetAt(0);

        List<Product> products = new ArrayList<>();
        sheet.forEach(row -> {
            if (row.getRowNum() == 0) {
                return; // Skip header row
            }

            products.add(
                new Product(
                    row.getCell(0).getStringCellValue(), // brandCode
                    row.getCell(1).getStringCellValue(), // partNumber
                    row.getCell(2).getStringCellValue(), // currentClassification
                    row.getCell(3) == null ? null : row.getCell(3).getStringCellValue(),
                    // longDescription
                    row.getCell(4).getStringCellValue(), // shortDescription
                    row.getCell(5).getStringCellValue().trim()// manualNewTaxonomy
                )
            );
        });

        Instant start = Instant.now();
        System.out.println("Starting reclassification of products...");

        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(280)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ofSeconds(70))
            .build();

        RateLimiter rateLimiter = RateLimiter.of("chatClientLimiter", config);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        products.forEach(product ->
            futures.add(
                CompletableFuture.runAsync(() -> product.setAutoNewTaxonomy(
                    RateLimiter.decorateSupplier(rateLimiter, () -> reclassifyProduct(product))
                        .get()))
            )
        );

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Instant end = Instant.now();
        System.out.printf("Reclassification completed in %d seconds.%n",
            Duration.between(start, end).getSeconds());

        AtomicInteger rowIndex = new AtomicInteger(0);
        products.forEach(product -> {
            sheet.getRow(rowIndex.incrementAndGet())
                .createCell(6)
                .setCellValue(product.getAutoNewTaxonomy());
            sheet.getRow(rowIndex.incrementAndGet())
                .createCell(7)
                .setCellValue(
                    product.getManualNewTaxonomy().equals(product.getAutoNewTaxonomy()) ? "Yes"
                        : "No");
        });

        FileOutputStream outputStream = new FileOutputStream(
            "/Users/dospina/Downloads/sample-data.xlsx");
        workbook.write(outputStream);
        workbook.close();
    }

    @Retryable(
        retryFor = {UnavailableException.class},
        backoff = @Backoff(delay = 5000, multiplier = 2)
    )
    private String reclassifyProduct(Product product) {
        ChatResponse response = chatClient.prompt()
            .advisors(advisor)
            .user(
                String.format(
                    """
                        Given the short and long description of a product, classify it into one of the possible categories in the format A:B:C:D
                        Response should always be one of the categories, if the product does not fit any category, return one of the categories that is the closest match.
                        Don't use any other format and don't add any additional text.
                        
                        Short Description:
                        - %s
                        Long Description:
                        - %s
                        """,
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
    private String recover(UnavailableException e, Product product) {
        // Log the error
        System.err.printf("Failed to reclassify product %s:%s after retries. Error: %s%n",
            product.getBrandCode(), product.getPartNumber(), e.getMessage());

        // Return a fallback value
        return "A:B:C:D";
    }

}
