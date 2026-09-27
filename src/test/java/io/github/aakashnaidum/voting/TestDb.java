package io.github.aakashnaidum.voting;

import io.github.aakashnaidum.voting.db.Database;
import io.github.aakashnaidum.voting.web.AppContext;
import java.util.UUID;

final class TestDb {
    private TestDb() {}

    /** A fresh, isolated in-memory database with the schema and reference data. */
    static AppContext freshApp() {
        Database db = new Database("jdbc:h2:mem:t" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        db.createSchema();
        db.runScript("/db/demo-data.sql");  // 3 constituencies + one OPEN election (id 1)
        return new AppContext(db);
    }
}
