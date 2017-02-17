import org.aeonbits.owner.Config;

@Config.Sources("file:AppConfig.properties")

public interface AppConfig extends Config{
    @Config.Key("github_token")
    String githubToken();

    @Config.Key("db")
    @DefaultValue("localhost:3306")
    String db();

    @Config.Key("db_user")
    @DefaultValue("kdf")
    String dbUser();

    @Config.Key("db_password")
    @DefaultValue("")
    String db_password();

    @Config.Key("stars_cutoff")
    @DefaultValue("100")
    int starsCutoff();
}
