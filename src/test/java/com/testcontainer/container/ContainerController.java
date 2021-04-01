package com.testcontainer.container;

import com.github.javafaker.Faker;
import com.testcontainer.api.Customer;
import com.testcontainer.api.ICustomerService;
import io.restassured.http.ContentType;
import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.testcontainer.databuilder.CustomerBuilder.customerWithIdAndName;
import static com.testcontainer.databuilder.CustomerBuilder.customerWithName;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.*;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
public class ContainerController extends ConfigContainer {

    private List<Customer> customerList;
    private Customer customerWithId1, customerWithId2;

    //MOCKED-SERVER: WEB-TEST-CLIENT(non-blocking client)'
    //SHOULD BE USED WITH 'TEST-CONTAINERS'
    //BECAUSE THERE IS NO 'REAL-SERVER' CREATED VIA DOCKER
    @Autowired
    WebTestClient mockedWebClient;

    @Autowired
    private ICustomerService service;

//    final ContentType CONT_ANY = ContentType.ANY;
//    final ContentType CONT_JSON = ContentType.JSON;
    final String REQ_MAP = "/customer";


    @BeforeAll
    static void beforeAll() {
        ConfigContainerTests.beforeAll();
    }


    @AfterAll
    static void afterAll() {
        ConfigContainerTests.afterAll();
    }


    @BeforeEach
    public void setUpLocal() {

        //REAL-SERVER INJECTED IN WEB-TEST-CLIENT(non-blocking client)'
        //SHOULD BE USED WHEN 'DOCKER-COMPOSE' UP A REAL-WEB-SERVER
        //BECAUSE THERE IS 'REAL-SERVER' CREATED VIA DOCKER-COMPOSE
        // realWebClient = WebTestClient.bindToServer()
        //                      .baseUrl("http://localhost:8080/customer")
        //                      .build();
        customerWithId1 = customerWithIdAndName(Faker.instance()
                                                     .idNumber()
                                                     .valid()).create();

        customerWithId2 = customerWithIdAndName(Faker.instance()
                                                     .idNumber()
                                                     .valid()).create();

        customerList = Arrays.asList(customerWithName().create(),
                                     customerWithName().create(),
                                     customerWithId1,
                                     customerWithId2
                                    );


        service.deleteAll()
               .thenMany(Flux.fromIterable(customerList))
               .flatMap(service::save)
               .doOnNext((item -> System.out.println("Inserted item is - TEST: " + item)))
               .blockLast(); // THATS THE WHY, BLOCKHOUND IS NOT BEING USED.
    }


    @Test
    public void saveWebTestClient() {

        final MediaType MTYPE_JSON = MediaType.APPLICATION_JSON;

        mockedWebClient
                .post()
                .uri(REQ_MAP)
                .body(Mono.just(customerWithId1),Customer.class)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .contentType(MTYPE_JSON)
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(customerWithId1.getId())
                .jsonPath("$.email")
                .isEqualTo(customerWithId1.getEmail())
                .jsonPath("$.rating")
                .isEqualTo(customerWithId1.getRating())
        ;
    }


    @Test
    public void save() {
        RestAssuredWebTestClient
                .given()
                .webTestClient(mockedWebClient)
                //                .header("Accept",CONT_ANY)
                //                .header("Content-type",CONT_JSON)
                .body(customerWithId1)

                .when()
                .post(REQ_MAP)

                .then()
                .log()
                .headers()
                .and()
                .log()
                .body()
                .and()
                //                .contentType(CONT_JSON)
                .statusCode(CREATED.value())

                //equalTo para o corpo do Json
                .body("email",containsString(customerWithId1.getEmail()));
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

                .body("id",hasItem(customerWithId1.getId()))
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