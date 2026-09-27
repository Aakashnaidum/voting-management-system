package io.github.aakashnaidum.voting.web;

import io.github.aakashnaidum.voting.config.AppConfig;
import io.github.aakashnaidum.voting.db.Database;
import io.github.aakashnaidum.voting.service.ElectionService;
import io.github.aakashnaidum.voting.service.UserService;
import io.github.aakashnaidum.voting.service.VotingService;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/** Wires the services once per web application and stores them in the servlet context. */
@WebListener
public class AppContext implements ServletContextListener {
    public final Database db;
    public final UserService users;
    public final ElectionService elections;
    public final VotingService voting;

    public AppContext() {
        this(new Database(AppConfig.dbUrl(), AppConfig.dbUser(), AppConfig.dbPassword()));
    }

    public AppContext(Database db) {
        this.db = db;
        this.users = new UserService(db);
        this.elections = new ElectionService(db);
        this.voting = new VotingService(db, elections);
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        db.createSchema();  // CREATE TABLE IF NOT EXISTS: safe to run on every start
        if (AppConfig.demoMode()) DemoData.load(this);
        String adminEmail = AppConfig.adminEmail();
        String adminPassword = AppConfig.adminPassword();
        if (!users.adminExists() && adminEmail != null && adminPassword != null) {
            users.createAdmin(adminEmail, adminPassword.toCharArray(), "Election Administrator");
        }
        event.getServletContext().setAttribute(AppContext.class.getName(), this);
    }

    public static AppContext from(ServletContext sc) {
        return (AppContext) sc.getAttribute(AppContext.class.getName());
    }
}
