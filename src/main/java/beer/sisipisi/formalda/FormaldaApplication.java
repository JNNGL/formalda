package beer.sisipisi.formalda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FormaldaApplication {

    public static void main(String[] args) {
        SpringApplication.run(FormaldaApplication.class, args);
    }

}
