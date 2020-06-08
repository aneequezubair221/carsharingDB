

import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import createObjects.CarFactory;
import createObjects.UserFactory;
import jdk.vm.ci.aarch64.AArch64;
import org.bson.BSON;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.neo4j.driver.*;
//import org.neo4j.driver.v1.*;
import redis.clients.jedis.Jedis;
import storedobjects.*;
import storedobjects.Query;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.neo4j.driver.Values.parameters;

//import static org.neo4j.driver.v1.Values.parameters;

public class Main implements AutoCloseable {
    private final Driver driverNeo4j;
    private final Jedis driverRedis;
    private final MongoClient mongoClient;

    public Main() {
        driverNeo4j = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "WJ7cbBLbwvuDaFF"));
        driverRedis = new Jedis("localhost");
        mongoClient = new MongoClient(new MongoClientURI("mongodb://localhost:27017"));
    }

    @Override
    public void close() throws Exception {
        driverNeo4j.close();
        driverRedis.close();
        mongoClient.close();
    }

    public void neo4jTest(final String message) {
        Session session = driverNeo4j.session();
        String greeting = session.writeTransaction(new TransactionWork<String>() {

            @Override
            public String execute(Transaction tx) {
                Result result = tx.run("Create (a:Greeting)" + "SET a.message = $message " + "RETURN a.message + ', from node ' + id(a)", parameters("message", message));
                System.out.println("neo4j");
                return result.single().get(0).asString();
            }
        });
        System.out.println(greeting);
    }


    public void redisTest(final String message) {
        redis.clients.jedis.Transaction transaction = driverRedis.multi();
        transaction.set("Testkey", message);
        transaction.get("Testkey");
        List<Object> result = transaction.exec();
        for (Object response : result) {
            System.out.println(response);
        }
    }

    public void mongoTest(final String message) {
        MongoDatabase mongoDatabase = mongoClient.getDatabase("test");
        MongoCollection<Document> mongoCollection = mongoDatabase.getCollection("testCollection");
        Document doc1 = new Document("key1", "val1").append("arg1", "valArg1");
        mongoCollection.insertOne(doc1);
        mongoCollection.find().forEach(new Block<Document>() {
            @Override
            public void apply(Document document) {
                System.out.println(document);
            }
        });
    }

    public static void main(String[] args) {
        try (Main main = new Main()) {
            main.neo4jTest("hello, world");
            main.redisTest("Redishallo");
            main.mongoTest("");
            //main.init();
            //main.addUser(main.createUser());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void storeCar(Car car, User owner) {

        //Mongo
        MongoDatabase mongoDatabase = mongoClient.getDatabase("CarSharing");
        MongoCollection<Document> mongoCollection = mongoDatabase.getCollection("car");
        Document doc1 = car.toDocument();
        mongoCollection.insertOne(doc1);
        car.setObjectID(doc1.getObjectId("_id").toString());

        //Neo4j
        Session session = driverNeo4j.session();
        String greeting = session.writeTransaction(new TransactionWork<String>() {
            @Override
            public String execute(Transaction tx) {
                Result result = tx.run(
                        "CREATE (a:Car{objectID:$cID, manufacturer: $manufacturer, seats:$seats, type:$type, fuelConsumption:$fuelConsumption, status:$status, fuelType:$fuelType})" +
                                "MERGE (l: Location{longitude:$longitude, latitude:$latitude})"+
                                "WITH (l)" +
                                "MATCH (u:User{objectID:$uID}) " +
                                "WITH (u)"+
                                "MERGE (u) -[:OWNES {offeringSince:$today}]-> (c) "+
                                "MERGE (c) -[:WAITING_HERE {FROM:$today}]-> (l) ",
                        parameters("$cID", car.getObjectID(),
                                "manufacturer", car.getManufacturer(),
                                "seats", car.getSeats(),
                                "type", car.getCarType(),
                                "fuelConsumption", car.getFuelConsumption(),
                                "longitude", car.getLongitude(),
                                "latitude", car.getLatitude(),
                                "status", car.getStatus(),
                                "fuelType", car.getFuelType(),
                                "uID", owner.getObjectID(),
                                "cID", car.getObjectID(),
                                "today", LocalDate.now()
                        ));
                return "done";
            }
        });
    }

    //Method to store a user in MongoDB and Neo4J
    public void addUser(User user) {


        //Mongo
        MongoDatabase mongoDatabase = mongoClient.getDatabase("CarSharing");
        MongoCollection<Document> mongoCollection = mongoDatabase.getCollection("user");
        Document doc1 = user.toDocument();

        mongoCollection.insertOne(doc1);
        System.out.println("Successfully added: " + doc1 + " to MongoDB");
        user.setObjectID(doc1.getObjectId("_id").toString());


        //Neo4j
        Session session = driverNeo4j.session();
        String greeting = session.writeTransaction(new TransactionWork<String>() {

            @Override
            public String execute(Transaction tx) {
                Result result = tx.run("Create (a:User{objectID:$id})", parameters("id", user.getObjectID()));
                return "done";
            }
        });
        System.out.println("Successfully added: " + user + " to Neo4J");
    }

    public void updateUser(User user){
        MongoDatabase mongoDatabase = mongoClient.getDatabase("CarSharing");
        MongoCollection<Document> mongoCollection = mongoDatabase.getCollection("user");
        Document doc1 = user.toDocument();

        mongoCollection.findOneAndUpdate(new Document("_id", user.getObjectID()),doc1);
    }

    public void updateCarStatus(Car car){
        MongoDatabase mongoDatabase = mongoClient.getDatabase("CarSharing");
        MongoCollection<Document> mongoCollection = mongoDatabase.getCollection("car");
        Document doc1 = car.toDocument();
        BasicDBObject update= new BasicDBObject();
        update.put("$set", new Document("Status", car.getStatus()));
        mongoCollection.findOneAndUpdate(new Document("_id", car.getObjectID()),update);

        //Neo4j
        Session session = driverNeo4j.session();
        String greeting = session.writeTransaction(new TransactionWork<String>() {

            @Override
            public String execute(Transaction tx) {
                Result result = tx.run(
                        "MATCH (c:Car{objectID : $c_ID})"+" SET c.status = $status"+" RETURN c.status",
                        parameters("c_ID", car.getObjectID(),
                                "status", car.getStatus()
                        ));
                return result.single().get(0).asString();
            }
        });
        System.out.println(car.getStatus());
    }

    public void updateCarLocation(Car car){
        MongoDatabase mongoDatabase = mongoClient.getDatabase("CarSharing");
        MongoCollection<Document> mongoCollection = mongoDatabase.getCollection("car");
        Document doc1 = car.toDocument();

        mongoCollection.findOneAndUpdate(new Document("_id", car.getObjectID()),doc1);

        //Neo4j
        Session session = driverNeo4j.session();
        String greeting = session.writeTransaction(new TransactionWork<String>() {

            @Override
            public String execute(Transaction tx) {
                Result result = tx.run(
                        "MATCH (c:Car{id:$c_ID})"+
                                "MERGE (l: Location{longitude:$longitude, latitude:$latitude})"+
                                "MERGE (c) -[:WAITING_HERE {FROM:$today}]-> (l) ",
                        parameters("c_ID", car.getObjectID(),
                                "status", car.getStatus(),
                                "today", LocalDate.now()

                        ));
                return "done";
            }
        });
    }



    public void init(){
        User alice =  new User(new Date(1998,5,23), null, "Alice BlueDress", new Address("Heidelberg", "WonderlandStreet","314", "67123"),
                "+49152514586436", "wonderland@redQueen.com", "DE15000523001125", "User",null);
        addUser(alice);
    }

    public void borrowCar(User user, Car car){
        //When? (Duration? Return ? Pickup?)
        //
    }

    public void returnCar(User user, Car car, Rating rating){
        //give rating (Maurice)
        //Timestamp
    }

    //Use Case 5 Maurice Chrisnach
    public void calculateCarRating(Car car){
        //Find all the Rating of the specific car
    }

    //Use Case 2 Maximilian Schuhmacher
    public void findHighDemandZone(){

    }
}
