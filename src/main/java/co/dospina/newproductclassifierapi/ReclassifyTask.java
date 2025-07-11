package co.dospina.newproductclassifierapi;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReclassifyTask {

    private final ReclassifyClient reclassifyClient;

    @Autowired
    public ReclassifyTask(ReclassifyClient reclassifyClient) {
        this.reclassifyClient = reclassifyClient;
    }

    public void run() throws IOException {
        FileInputStream file = new FileInputStream("/Users/dospina/nmg/Vertex AI + RAG/sample_data.xlsx");
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
                    row.getCell(3).getStringCellValue(), // currentClassificationDescription
                    row.getCell(4) == null ? null : row.getCell(4).getStringCellValue(),
                    // longDescription
                    row.getCell(5).getStringCellValue(), // shortDescription
                    row.getCell(6).getStringCellValue().trim()// manualNewTaxonomy
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
                    RateLimiter.decorateSupplier(rateLimiter,
                        () -> reclassifyClient.reclassifyProduct(product)).get()))
            )
        );

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Instant end = Instant.now();
        System.out.printf("Reclassification completed in %d seconds.%n",
            Duration.between(start, end).getSeconds());

        AtomicInteger rowIndex = new AtomicInteger(0);
        products.forEach(product -> {
            Row row = sheet.getRow(rowIndex.incrementAndGet());
            row.createCell(7).setCellValue(product.getAutoNewTaxonomy());
            row.createCell(8).setCellValue(
                product.getManualNewTaxonomy().equals(product.getAutoNewTaxonomy()) ? "Yes" : "No");
        });

        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        evaluator.evaluateAll();

        FileOutputStream outputStream = new FileOutputStream(getNextSampleDataFilename("/Users/dospina/nmg/Vertex AI + RAG"));
        workbook.write(outputStream);
        workbook.close();
    }

    private String getNextSampleDataFilename(String directory) {
        File dir = new File(directory);
        File[] files = dir.listFiles((d, name) -> name.matches("sample_data(\\d*)\\.xlsx"));
        int max = 0;
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String num = name.replaceAll("sample_data(\\d*)\\.xlsx", "$1");
                int n = num.isEmpty() ? 0 : Integer.parseInt(num);
                if (n > max) max = n;
            }
        }
        int next = max + 1;
        return directory + "/sample_data" + (next == 1 ? "" : next) + ".xlsx";
    }

}
