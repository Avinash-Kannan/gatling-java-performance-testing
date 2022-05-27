package gatlingeCommerce;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.sun.jdi.PrimitiveValue;
import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import io.gatling.javaapi.jdbc.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import static io.gatling.javaapi.jdbc.JdbcDsl.*;

public class eCommerceSimulation extends Simulation {

  private static final String domainName = "demostore.gatling.io";

  private static final HttpProtocolBuilder httpProtocol = http
    .baseUrl("https://"+domainName);

  private static final int userCount = Integer.parseInt(System.getProperty("USERS","5"));
  private static final Duration rampDuration =
          Duration.ofSeconds(Integer.parseInt(System.getProperty("RAMP_DURATION","10")));
  private static final Duration testDuration =
          Duration.ofSeconds(Integer.parseInt(System.getProperty("TEST_DURATION","80")));

  private  static final FeederBuilder<String> categoryFeeder =
          csv("dataFeeder/category.csv").random();

  private static final FeederBuilder<Object> productFeeder =
          jsonFile("dataFeeder/product.json").random();

  private static final FeederBuilder<String> loginFeeder =
          csv("dataFeeder/login.csv").circular();

  private static final ChainBuilder initSession =
          exec(flushCookieJar())
                  .exec(session -> session.set("randomNumber", ThreadLocalRandom.current().nextInt()))
                  .exec(session -> session.set("LoggedIn",false))
                  .exec(session -> session.set("cartPrice", 0.00))
                  .exec(addCookie(Cookie("sessionID", "eCommerce").withDomain(domainName)));

  private static class eCommercePages {

    private static final ChainBuilder homePage =
            exec(
               http("Load eCommerce Home Page")
              .get("/")
              .check(regex("<title>Gatling Demo-Store</title>").exists())
              .check(css("#_csrf","content").saveAs("csrfValue")));

    private static final ChainBuilder aboutPage =
            exec(
              http("Load About Us Page")
              .get("/about-us")
              .check(substring("About Us"))
            );

    private static final ChainBuilder categoryPage =
          feed(categoryFeeder)
          .exec(
              http("Load Category - #{categoryName}")
              .get("/category/#{categoryEndpoint}")
              .check(css("#CategoryName").isEL("#{categoryName}"))
          );

    private static final ChainBuilder addProduct =
            feed(productFeeder)
            .exec(
                 http("Add Product #{name} to Cart")
                 .get("/cart/add/#{id}")
                 .check(substring(" items in your cart").exists()))
                    .exec(session -> session.set("cartPrice",session.getDouble("cartPrice")
                            + session.getDouble("price")))
                    .exec(session -> {
                        System.out.println("Updated Cart Price : " + session.getDouble("cartPrice"));
                        return session;
                    }
            );

    private static final ChainBuilder loginPage =
            feed(loginFeeder)
            .exec(session -> {
                      System.out.println("User Logged In : " + session.get("LoggedIn"));
                      return session;
            })
           .exec(
              http("Login eCommerce site")
               //.post("http://" + domainName + "/login")
              .post("/login")
              //.check(substring("Login").exists())
              .formParam("_csrf", "#{csrfValue}")
              .formParam("username", "#{username}")
              .formParam("password", "#{password}")
              .check(status().is(200)))
           .exec(session -> session.set("LoggedIn",true))
           .exec(session -> {
                      System.out.println("User Logged In : " + session.get("LoggedIn"));
                      return session;
                    });

    private static final ChainBuilder viewCart =
            doIf(session -> !session.getBoolean("LoggedIn"))
                    .then(exec(loginPage))
                    .exec(
                            http("View Cart Page")
                             .get("/cart/view")
                                    .check(css("#grandTotal").isEL("$#{cartPrice}"))
                    );


    private static final ChainBuilder productCheckout =
             exec(
              http("Check Out Product")
              .get("/cart/checkout")
               .check(substring("Thanks for your order!").exists())
    );

  }


  private ScenarioBuilder scn = scenario("eCommerceSimulation")
     .exec(initSession)
    .exec(eCommercePages.homePage)
    .pause(2)
    .exec(eCommercePages.aboutPage)
    .pause(2)
    .exec(eCommercePages.categoryPage )
    .pause(2)
    .exec(eCommercePages.addProduct)
    .pause(2)
    .exec(eCommercePages.viewCart)
    .pause(2)
    .exec(eCommercePages.productCheckout);

  {
      setUp((scn.injectOpen(
              rampUsers(userCount).during(rampDuration)
      )).protocols(httpProtocol));
      /* open model
      setUp(scn.injectOpen(
              atOnceUsers(3),
              nothingFor(Duration.ofSeconds(5))
              rampUsers(10).during(Duration.ofSeconds(10)),
              nothingFor(Duration.ofSeconds(5)),
              constantUsersPerSec(5).during(Duration.ofSeconds(10))
        )).protocols(httpProtocol);
       */

      /* Closed model
      setUp(scn.injectClosed(
              constantConcurrentUsers(3).during(Duration.ofSeconds(20)),
              rampConcurrentUsers(1).to(5).during(Duration.ofSeconds(10))
      )).protocols(httpProtocol);
       */

      /* throttle
      setUp(scn.injectOpen(
              constantUsersPerSec(1).during(Duration.ofSeconds(60)))
              .protocols(httpProtocol)
              .throttle(
                      reachRps(10).in(Duration.ofSeconds(20)),
                      holdFor(Duration.ofSeconds(60)),
                      jumpToRps(20),
                      holdFor(Duration.ofSeconds(60))
              ))
              .maxDuration(Duration.ofMinutes(3));
       */
  }
}
