package db.migration;

import com.apollo.elevator.customer.service.CustomerCodeGenerator;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Converts every existing customer code to the APL-ELV-&lt;initials&gt;-&lt;id&gt; format. */
public class V22__customer_code_format extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Map<Long, String> codes = new LinkedHashMap<>();
        try (Statement select = context.getConnection().createStatement();
             ResultSet rows = select.executeQuery("SELECT id, customer_name FROM customer ORDER BY id")) {
            while (rows.next()) {
                long id = rows.getLong("id");
                codes.put(id, CustomerCodeGenerator.generate(rows.getString("customer_name"), id));
            }
        }
        if (codes.isEmpty()) {
            return;
        }

        // Two passes so a new code can never clash with another row's not-yet-converted code.
        try (Statement placeholder = context.getConnection().createStatement()) {
            placeholder.executeUpdate("UPDATE customer SET customer_code = 'TMP-MIGRATE-' || id");
        }
        try (PreparedStatement update = context.getConnection()
                .prepareStatement("UPDATE customer SET customer_code = ? WHERE id = ?")) {
            for (Map.Entry<Long, String> entry : codes.entrySet()) {
                update.setString(1, entry.getValue());
                update.setLong(2, entry.getKey());
                update.addBatch();
            }
            update.executeBatch();
        }
    }
}
