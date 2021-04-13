package com.testcontainer.container;

import com.testcontainer.api.Customer;
import com.testcontainer.api.ICustomerRepo;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.testcontainer.databuilder.CustomerBuilder.customerWithName;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContainerRepo extends ConfigContainerTests {

    private Customer customer1;
    private Customer customer2;
    private Customer customer3;
    private List<Customer> customerList;

    @Autowired
    private ICustomerRepo repo;


    @BeforeAll
    static void beforeAll() {
        ConfigContainerTests.beforeAll();
    }


    @AfterAll
    static void afterAll() {
        ConfigContainerTests.afterAll();
    }


    @BeforeEach
    void setUp() {
        customer1 = customerWithName().create();
        customer2 = customerWithName().create();
        customer3 = customerWithName().create();
        customerList = Arrays.asList(customer1,customer3);
    }


    private void cleanDbToTest() {
        StepVerifier
                .create(repo.deleteAll())
                .expectSubscription()
                .verifyComplete();

        System.out.println("\n\n==================> CLEAN-DB-TO-TEST" +
                                   " <==================\n\n");
    }


    @Test
    void checkContainer() {
        assertTrue(container.isRunning());
    }


    @Test
    @DisplayName("Save: Object")
    public void save_obj() {
        cleanDbToTest();

        StepVerifier
                .create(repo.save(customer2))
                .expectSubscription()
                .expectNext(customer2)
                .verifyComplete();
    }


    @Test
    @DisplayName("find: Count")
    public void find_count() {
        final Flux<Customer> customerFlux =
                repo.deleteAll()
                    .thenMany(Flux.fromIterable(customerList))
                    .flatMap(repo::save)
                    .doOnNext(item -> repo.findAll());

        StepVerifier
                .create(customerFlux)
                .expectSubscription()
                .expectNextCount(2)
                .verifyComplete();

        //        StepVerifier
        //                .create(repo.findAll())
        //                .expectSubscription()
        //                .expectNextCount(customerList.toArray().length)
        //                .verifyComplete();
    }


    @Test
    @DisplayName("Find: Objects Content")
    public void find_1() {

        final Flux<Customer> customerFlux =
                repo.deleteAll()
                    .thenMany(Flux.fromIterable(customerList))
                    .flatMap(repo::save)
                    .doOnNext(item -> repo.findAll());

        StepVerifier
                .create(customerFlux)
                .expectSubscription()
                .expectNextMatches(customer -> customer1.getEmail()
                                                        .equals(customer.getEmail()))
                .expectNextMatches(customer -> customer3.getEmail()
                                                        .equals(customer.getEmail()))
                .verifyComplete();
    }


    @Test
    @DisplayName("Find: Objects")
    public void find_2() {

        final Flux<Customer> customerFlux =
                repo.deleteAll()
                    .thenMany(Flux.fromIterable(customerList))
                    .flatMap(repo::save)
                    .doOnNext(item -> repo.findAll());

        StepVerifier
                .create(customerFlux)
                .expectNext(customer1)
                .expectNext(customer3)
                .verifyComplete();
    }


    @Test
    @DisplayName("Delete: Count")
    public void deleteAll_count() {

        StepVerifier
                .create(repo.deleteAll())
                .expectSubscription()
                .verifyComplete();

        Flux<Customer> fluxTest = repo.findAll();

        StepVerifier
                .create(fluxTest)
                .expectSubscription()
                .expectNextCount(0)
                .verifyComplete();

    }


    @Test
    @DisplayName("BHWorks")
    public void bHWorks() {
        try {
            FutureTask<?> task = new FutureTask<>(() -> {
                Thread.sleep(0);
                return "";
            });

            Schedulers.parallel()
                      .schedule(task);

            task.get(10,TimeUnit.SECONDS);
            Assertions.fail("should fail");
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Assertions.assertTrue(e.getCause() instanceof BlockingOperationError,"detected");
        }
    }
}