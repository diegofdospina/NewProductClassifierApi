package co.dospina.newproductclassifierapi;

import java.io.IOException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class NewProductClassifierApiApplication {

    public static void main(String[] args) throws IOException {
        ConfigurableApplicationContext context = SpringApplication.run(
            NewProductClassifierApiApplication.class, args);

        ReclassifyTask reClassifyTask = context.getBean(ReclassifyTask.class);
        reClassifyTask.run();
    }

}
