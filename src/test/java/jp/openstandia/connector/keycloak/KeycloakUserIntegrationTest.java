package jp.openstandia.connector.keycloak;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for some Keycloak user operations.
 * integration.properties need to be supplied with working values in order for these tests to pass.
 */
@Disabled
public class KeycloakUserIntegrationTest {
  private static final Log LOG = Log.getLog(KeycloakUserIntegrationTest.class);

  private static ArrayList<ConnectorObject> results = new ArrayList<>();

  private KeycloakConfiguration configuration;

  @BeforeEach
  public void setup() throws IOException {
    Properties properties = new Properties();
    properties.load(Files.newInputStream(Paths.get("src/test/resources/integration.properties")));
    assertEquals(6, properties.size());
    configuration = new KeycloakConfiguration();
    configuration.setClientId(properties.get("clientId").toString());
    configuration.setUsername(properties.get("username").toString());
    configuration.setPassword(new GuardedString(properties.get("password").toString().toCharArray()));
    configuration.setServerUrl(properties.get("serverUrl").toString());
    configuration.setTargetRealmName(properties.get("targetRealmName").toString());
    configuration.setRealmName(properties.get("realmName").toString());

    // Execute Actions test
    configuration.setRequiredActionsOnUserCreate(new String[]{"TERMS_AND_CONDITIONS", "VERIFY_EMAIL"});
  }

  protected APIConfiguration apiConfig() {
    APIConfiguration impl = TestHelpers.createTestConfiguration( KeycloakConnector.class, configuration);
    impl.getResultsHandlerConfiguration().setFilteredResultsHandlerInValidationMode(true);
    return impl;
  }

  public static SearchResultsHandler handler = new SearchResultsHandler() {

    @Override
    public boolean handle(ConnectorObject connectorObject) {
      results.add(connectorObject);
      return true;
    }

    @Override
    public void handleResult(SearchResult result) {
      LOG.info("Im handling {0}", result.getRemainingPagedResults());
    }
  };

  @Test
  public void schema() {
    ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
    Schema schema = factory.newInstance(apiConfig()).schema();
    assertNotNull(schema);
    assertNotNull(schema.getObjectClassInfo());
    assertFalse(schema.getObjectClassInfo().isEmpty());
    LOG.info(schema.toString());
  }

  @Test
  public void test() {
    ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
    factory.newInstance(apiConfig()).test();
  }

  @Test
  public void validate() {
    ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
    factory.newInstance(apiConfig()).validate();
  }

  @Test
  public void createUser() {
    ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
    Set<Attribute> attributes = CollectionUtil.newSet(
            AttributeBuilder.build(Name.NAME, "mtrout"),
            AttributeBuilder.build(AttributeUtil.createSpecialName("ENABLE"), true),
            AttributeBuilder.build("lastName", "Trout"),
            AttributeBuilder.build("firstName", "Mike"),
            AttributeBuilder.build("email", "mtrout@mlb.com"),
            AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("MyGreatSecret1111!".toCharArray()))
    );
    Uid createResponseUid = factory.newInstance(apiConfig()).create( new ObjectClass("user"), attributes, null);
    LOG.info("New user created with uid: " + createResponseUid.getUidValue());
    assertNotNull(createResponseUid);
    assertNotNull(createResponseUid.getUidValue());
  }

  @Test
  public void searchUser() {
    results = new ArrayList<>();
    ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();

    factory.newInstance(apiConfig()).search( new ObjectClass("user"), null, handler, new OperationOptionsBuilder().setAttributesToGet("groups").build());
    assertTrue( 1 < results.size());
  }

  @Test
  public void searchUserByUid() {
    results = new ArrayList<>();
    ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
    Attribute idAttribute = new AttributeBuilder().setName(Uid.NAME).addValue("99990ffc-8ae0-459b-a412-5b43fa7d9999").build();

    factory.newInstance(apiConfig()).search( new ObjectClass("user"), new EqualsFilter(idAttribute), handler, new OperationOptionsBuilder().setAttributesToGet("groups").build());
    assertEquals( 1, results.size());
  }



  @Test
  public void searchRole() {
    results = new ArrayList<>();
    ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();

    factory.newInstance(apiConfig()).search( new ObjectClass("clientRole"), null, handler, null);
    assertTrue( 1 < results.size());
  }
}
