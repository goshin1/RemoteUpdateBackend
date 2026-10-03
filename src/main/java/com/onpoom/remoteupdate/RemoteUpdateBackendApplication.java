package com.onpoom.remoteupdate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RemoteUpdateBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(RemoteUpdateBackendApplication.class, args);
	}

}
