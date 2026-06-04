package hu.jgj52.authy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Authy {

    @Inject
    private Logger logger;
    private JsonObject config;
    private DataSource ds;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Plugin initialization logic goes here
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        File file = new File(
                Path.of("plugins", "authy", "config.json").toUri()
        );
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        try (FileInputStream fis = new FileInputStream(file)) {
            config = gson.fromJson(new InputStreamReader(fis), JsonObject.class);
        } catch (IOException e) {
            config = new JsonObject();

            JsonObject postgres = new JsonObject();
            postgres.addProperty("host", "127.0.0.1");
            postgres.addProperty("port", 5432);
            postgres.addProperty("database", "postgres");
            postgres.addProperty("user", "postgres");
            postgres.addProperty("password", "");

            config.add("postgres", postgres);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(gson.toJson(config).getBytes());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        try {
            Class.forName("org.postgresql.Driver"); // doesn't load without this
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        JsonObject postgres = config.get("postgres").getAsJsonObject();
        String dbHost = postgres.get("host").getAsString();
        String dbPort = postgres.get("port").getAsString();
        String dbDatabase = postgres.get("database").getAsString();
        String dbUser = postgres.get("user").getAsString();
        String dbPassword = postgres.get("password").getAsString();

        HikariConfig config = new HikariConfig();

        config.setJdbcUrl("jdbc:postgresql://" +
                dbHost + ":" +
                dbPort + "/" +
                dbDatabase
        );
        config.setUsername(dbUser);
        config.setPassword(dbPassword);

        ds = new HikariDataSource(config);
    }

    @Subscribe
    public void onLogin(PreLoginEvent event) {
        if (event.getUniqueId() == null) return;
        try (Connection conn = ds.getConnection()) {
            PreparedStatement ps = conn.prepareStatement("""
                    SELECT token FROM players WHERE uuid = ?::uuid
                    LIMIT 1
            """);

            ps.setString(1, event.getUniqueId().toString());

            ResultSet rs = ps.executeQuery();

            String token = rs.next() ? "$" + rs.getString("token") + "$" : "$-$";

            event.setResult(
                    PreLoginEvent.PreLoginComponentResult.denied(
                            Component.empty()
                                    .append(Component.translatable("capey.message.token.your").color(NamedTextColor.GREEN))
                                    .append(Component.newline())
                                    .append(Component.text(token))
                                    .append(Component.newline())
                                    .append(Component.newline())
                                    .append(Component.translatable("capey.message.token.share").color(NamedTextColor.RED).decorate(TextDecoration.BOLD))
                                    .append(Component.newline())
                                    .append(Component.translatable("capey.message.token.auto").color(NamedTextColor.GREEN))
                    )
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
