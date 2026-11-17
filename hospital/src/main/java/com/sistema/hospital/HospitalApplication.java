package com.sistema.hospital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// QA: CU-00/03/04 piden "catálogos precargados en caché"; sin esto no existía ninguna caché (ver CatalogoService)
@EnableCaching
// CU-12: habilita el recordatorio automático de citas de seguimiento (@Scheduled)
@EnableScheduling
public class HospitalApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalApplication.class, args);
    }

}
