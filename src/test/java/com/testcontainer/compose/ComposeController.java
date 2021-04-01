package com.testcontainer.compose;

import com.github.javafaker.Faker;
import com.testcontainer.api.Customer;
import com.testcontainer.api.ICustomerService;
import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.testcontainer.databuilder.CustomerBuilder.customerWithIdAndName;
import static com.testcontainer.databuilder.CustomerBuilder.customerWithName;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.http.HttpStatus.*;

public class ComposeController extends ConfigControllerTests {

    @Container
    private static final DockerComposeContainer<?> compose = new ConfigComposeTests().compose;

    private Customer customerWithId;

    // MOCKED-SERVER: WEB-TEST-CLIENT(non-blocking client)'
    // SHOULD BE USED WITH 'TEST-CONTAINERS'
    // BECAUSE THERE IS NO 'REAL-SERVER' CREATED VIA DOCKER-COMPOSE
    @Autowired
    WebTestClient mockedWebClient;

    @Autowired
    private ICustomerService service;

    final String REQ_MAP = "/customer";


    @BeforeAll
    static void beforeAll() {
        ConfigComposeTests.beforeAll();
    }


    @AfterAll
    static void afterAll() {
        compose.close();
        ConfigComposeTests.afterAll();
    }


    @BeforeEach
    public void setUpLocal() {

        //REAL-SERVER INJECTED IN WEB-TEST-CLIENT(non-blocking client)'
        //SHOULD BE USED WHEN 'DOCKER-COMPOSE' UP A REAL-WEB-SERVER
        //BECAUSE THERE IS 'REAL-SERVER' CREATED VIA DOCKER-COMPOSE
        // realWebClient = WebTestClient.bindToServer()
        //                      .baseUrl("http://localhost:8080/customer")
        //                      .build();

        customerWithId = customerWithIdAndName(Faker.instance()
                                                    .idNumber()
                                                    .valid()).create();

        List<Customer> customerList = asList(customerWithName().create(),
                                             customerWithName().create(),
                                             customerWithName().create(),
                                             customerWithId
                                            );

        service.deleteAll()
               .thenMany(Flux.fromIterable(customerList))
               .flatMap(service::save)
               .doOnNext((item -> System.out.println("Inserted item is - TEST: " + item)))
               .blockLast(); // THATS THE WHY, BLOCKHOUND IS NOT BEING USED.
    }


    @Test
    public void save() {
        RestAssuredWebTestClient
                .given()
                .webTestClient(mockedWebClient)
                //                .header("Accept",CONT_ANY)
                //                .header("Content-type",CONT_JSON)
                .body(customerWithId)

                .when()
                .post(REQ_MAP)

                .then()
                .statusCode(CREATED.value())
        ;
    }


    @Test
    public void findAll() {
        RestAssuredWebTestClient
                .given()
                .webTestClient(mockedWebClient)

                .when()
                .get(REQ_MAP)

                .then()
                .statusCode(OK.value())
                .log()
                .headers()
                .and()
                .log()
                .body()
                .and()

                .body("id",hasItem(customerWithId.getId()))
        ;
    }


    @Test
    public void deleteAll() {
        RestAssuredWebTestClient
                .given()
                .webTestClient(mockedWebClient)

                .when()
                .delete(REQ_MAP)

                .then()
                .statusCode(NO_CONTENT.value())
        ;

    }


    @Test
    public void blockHoundWorks() {
        try {
            FutureTask<?> task = new FutureTask<>(() -> {
                Thread.sleep(0);
                return "";
            });

            Schedulers.parallel()
                      .schedule(task);

            task.get(10,TimeUnit.SECONDS);
            fail("should fail");
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            assertTrue(e.getCause() instanceof BlockingOperationError,"detected");
        }
    }
}