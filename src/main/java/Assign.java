import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.aeonbits.owner.ConfigFactory;


/**
 * Assigned discovered github projects (found using Discover class) to editors on AI Resources site.
 * This class takes as input the github_project entries.
 * It outputs to the resource and resource_category table.
 * It creates pending projects in the root Artificial Intelligence category (cat_id:0)
 * 
 * @author ksee
 *
 */
public class Assign {
  private AppConfig params;
  Connection connect;

  public Assign() throws SQLException {
    try {
      Class.forName("com.mysql.jdbc.Driver");
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      System.out.println("Fatal Error: Unable to initialize JDBC driver.  Exiting ...");
      System.exit(1);
    }

    params = ConfigFactory.create(AppConfig.class);

    connect = DriverManager.getConnection("jdbc:mysql://localhost:3306/openair5", params.dbUser(), params.db_password());
  }
  
  public void execute() throws SQLException {
    String querySql = "SELECT identifier FROM github_project";
    String insertResourceSql = "INSERT INTO resource(link,description,submitter_id) VALUES(?,'',1)";
    String insertCategoryResourceSql = "INSERT INTO resource_category(resource_id, category_id) VALUES(?,0)";
    try (
        Statement queryStmt = connect.createStatement();
        ResultSet rs = queryStmt.executeQuery(querySql);
        PreparedStatement insertResourceStmt = connect.prepareStatement(insertResourceSql, Statement.RETURN_GENERATED_KEYS);
        PreparedStatement insertResourceCategoryStmt = connect.prepareStatement(insertCategoryResourceSql);
        ) {
      while(rs.next()) {
        String identifier = rs.getString(1);
        String link = "https://github.com/"+identifier;
        insertResourceStmt.setString(1, link);
        insertResourceStmt.executeUpdate();
        
        try (ResultSet generatedKeys = insertResourceStmt.getGeneratedKeys()) {
          if (generatedKeys.next()) {
              long resource_id = generatedKeys.getLong(1);
              insertResourceCategoryStmt.setLong(1, resource_id);
              insertResourceCategoryStmt.executeUpdate();
          }
          else {
              throw new SQLException("Creating user failed, no ID obtained.");
          }
        }    
      }
    }
  }
  
  public void shutdown() throws SQLException {
    connect.close();
  }
  
  public static void main(String[] args) throws SQLException {
    Assign a = new Assign();
    a.execute();
    a.shutdown();
  }
}
