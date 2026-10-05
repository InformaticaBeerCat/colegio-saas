package cl.colegiosaas;

import org.springframework.boot.SpringApplication;

public class TestColegioSaasApplication {

	public static void main(String[] args) {
		SpringApplication.from(ColegioSaasApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
