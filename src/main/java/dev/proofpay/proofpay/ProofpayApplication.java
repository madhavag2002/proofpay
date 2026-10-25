package dev.proofpay.proofpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ProofpayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProofpayApplication.class, args);
	}

}
