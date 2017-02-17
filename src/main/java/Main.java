import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.Buffer;
import java.util.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.codec.binary.Base64;

public class Main {
    private AppConfig params;
    private String baseUrl;
    private String githubToken;
    Gson gson = new Gson();
    Connection connect;

    public Main() throws SQLException {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.out.println("Fatal Error: Unable to initialize JDBC driver.  Exiting ...");
            System.exit(1);
        }

        params = ConfigFactory.create(AppConfig.class);
        baseUrl = "https://api.github.com/";
        githubToken = params.githubToken();

        connect = DriverManager.getConnection("jdbc:mysql://localhost:3306/openair5", params.dbUser(), params.db_password());
    }

    public static void main(String[] args) throws IOException {
        if(args[0] == null){
            System.out.println("No argument given, please supply an argument of either gofish, or assign");
            System.exit(1);
        }

        Main main = null;

        try {
            main = new Main();
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Fatal Error: Unable to connect to database, Exiting ...");
            System.exit(1);
        }

        if(args[0].equals("gofish")){
            System.out.println("using keyword list to find new projects from github ...");
            main.goFish();
        } else if(args[0].equals("assign")){

        }
    }

    private void assign() throws SQLException {
        ArrayList<String> githubProjects = new ArrayList<String>();
        PreparedStatement statement = connect.prepareStatement("SELECT identifier FROM openair5.github_project");
        ResultSet rs = statement.executeQuery();
        while (rs.next()){
            githubProjects.add(rs.getString("identifier"));
        }
        System.out.println("please type the name of the editor you would like to assign github projects to and press enter:");
        Scanner scan = new Scanner(System.in);
        String editor = scan.next();
        System.out.println("Please enter the number of projects you would like to assign to " + editor + " and press enter:");
        int numProjects = scan.nextInt();
        System.out.println("Assigning " + numProjects + " to " + editor);


    }

    private ArrayList<String> getExistingResourceLinks(){
        ArrayList<String> resourceLinks = new ArrayList<String>();
        
    }

    private void goFish() {

        List<String> keywords = null;

        try {
            keywords = getKeywordsFromDb();
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Fatal Error occurred when trying to get keywords from DB, exiting ...");
            System.exit(1);
        }

        Map<String, Integer> foundProjects = null;

        try {
            foundProjects = getPopularProjectsForKeywordList(keywords, params.starsCutoff());
            System.out.println("found " + foundProjects.keySet().size() + " projects");
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("Fatal error occurred searching for projects on github, exiting ...");
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Fatal error occurred searching for projects on github, exiting ...");
            System.exit(1);
        }

        Map<String, Integer> newProjects = null;

        try {
            newProjects = filterOutOldProjects(foundProjects);
            System.out.println(newProjects.keySet().size() + " of found projects were new");
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Fatal error occurred determining which found projects were new.  Exiting");
            System.exit(1);
        }

        saveProjectsToDb(newProjects);

        System.out.println("Completed!");
    }

    private void saveProjectsToDb (Map<String, Integer> projects){
        for(String product: projects.keySet()){
            try {
                PreparedStatement statement = connect.prepareStatement("INSERT INTO openair5.github_project (identifier, stars) VALUES (?, ?)");
                statement.setString(1, product);
                statement.setInt(2, projects.get(product));
                statement.execute();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private Map<String,Integer> filterOutOldProjects(Map<String, Integer> foundProjects) throws SQLException {
        Map<String, Integer> existingProjects = new HashMap<String, Integer>();
        PreparedStatement statement = connect.prepareStatement("SELECT identifier FROM openair5.github_project");
        ResultSet rs = statement.executeQuery();
        while (rs.next()){
            existingProjects.put(rs.getString("identifier"), 1);
        }

        Map<String, Integer> newProjects = new HashMap<String, Integer>();

        for (String project: foundProjects.keySet()){
            if(!existingProjects.containsKey(project)){
                newProjects.put(project, foundProjects.get(project));
            }
        }

        return newProjects;
    }

    private List<String> getKeywordsFromDb() throws SQLException {
        ArrayList<String> keywords = new ArrayList<String>();
        PreparedStatement statement = connect.prepareStatement("SELECT name FROM openair5.keywords");
        ResultSet rs = statement.executeQuery();
        while (rs.next()){
            keywords.add(rs.getString("name"));
        }
        return keywords;
    }

    private Map<String, Integer> getPopularProjectsForKeywordList(List<String> keywords, int starCuttoff) throws IOException, InterruptedException {
        Map<String, Integer> projects = new HashMap<String, Integer>();
        int totalCalls = 0;
        for(String keyword: keywords){
            totalCalls = getPopularProjectsForKeyword(keyword, starCuttoff, projects, "", totalCalls);
            totalCalls = getPopularProjectsForKeyword(keyword, starCuttoff, projects, "in:readme", totalCalls);
        }
        return projects;
    }

    private int getPopularProjectsForKeyword(String keyword, int starCuttoff, Map<String, Integer> projects, String searchModifier, int totalCalls) throws IOException, InterruptedException {
        int keywordCalls = 0;
        boolean continuation = true;
        while (continuation){
            RepoSearchResponse data = searchRepositoriesSimple(keyword, "stars", searchModifier, keywordCalls + 1);
            keywordCalls += 1;
            totalCalls += 1;
            for(Repo item : data.getItems()){
                if(item.getStargazers_count() < starCuttoff){
                    continuation = false;
                    break;
                }
                projects.put(item.getFull_name(), item.getStargazers_count());
            }
            if (keywordCalls >= 10 || keywordCalls * 100 > data.getTotal_count()){
                continuation = false;
            }
            if (totalCalls % 30 == 0){
                Thread.sleep(60000);
            }
        }
        return totalCalls;
    }

    private RepoSearchResponse searchRepositoriesSimple(String query, String sort, String modifier, int page) throws IOException {
        String queryUrlString = baseUrl + "search/repositories?q='" + query + "'" + modifier + "+&sort=" + sort + "&per_page=100";
        if (page > 1){
            queryUrlString += "&page=" + page;
        }
        System.out.println(queryUrlString);
        String responseString = getAuthenticatedResponse(queryUrlString);
        return gson.fromJson(responseString, RepoSearchResponse.class);
    }

    private String getAuthenticatedResponse(String urlString) throws IOException {
        urlString = urlString.replace(" ", "%20");
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        String token = this.githubToken + ":x-oauth-basic";
        String authString = "Basic " + Base64.encodeBase64String(token.getBytes());

        con.setRequestProperty("Authorization", authString);
        InputStream in = con.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String line;
        String responseString = "";
        while ((line = br.readLine()) != null){
            responseString += line;
        }
        return responseString;
    }

    private class Repo {
        private String full_name;
        private int stargazers_count;

        public String getFull_name() {
            return full_name;
        }

        public int getStargazers_count() {
            return stargazers_count;
        }

        public void setFull_name(String full_name) {
            this.full_name = full_name;
        }

        public void setStargazers_count(int stargazers_count) {
            this.stargazers_count = stargazers_count;
        }
    }

    private class RepoSearchResponse {
        private Repo[] items;
        private int total_count;

        public Repo[] getItems() {
            return items;
        }

        public void setItems(Repo[] items) {
            this.items = items;
        }

        public int getTotal_count() {
            return total_count;
        }

        public void setTotal_count(int totalCount) {
            this.total_count = totalCount;
        }
    }
}
