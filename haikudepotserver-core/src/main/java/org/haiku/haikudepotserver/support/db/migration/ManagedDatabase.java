/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.db.migration;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * <p>This object takes responsibility for setting up the database in the first place and also
 * ensuring that any database migrations are applied before the application finishes starting-up.
 * The SQL files to run the migrations are in the resources of this project.  The system uses the
 * flyway project to achieve this.</p>
 */

public class ManagedDatabase {

    protected static Logger LOGGER = LoggerFactory.getLogger(ManagedDatabase.class);

    private DataSource dataSource;

    private Boolean migrate = null;

    private Boolean validateOnMigrate = null;

    private String schema;

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setMigrate(Boolean migrate) {
        this.migrate = migrate;
    }

    public void setValidateOnMigrate(Boolean validateOnMigrate) {
        this.validateOnMigrate = validateOnMigrate;
    }

    public void init() {
        migrate();
    }

    private void migrate() {
        if (null == migrate || migrate) {
            Preconditions.checkNotNull(getDataSource());
            Preconditions.checkState(!Strings.isNullOrEmpty(getSchema()));

            Flyway flyway = new Flyway();
            flyway.setSchemas(getSchema());
            flyway.setLocations(String.format("db/%s/migration", getSchema()));
            flyway.setValidateOnMigrate(null==validateOnMigrate || validateOnMigrate);
            flyway.setDataSource(dataSource);

            LOGGER.info("will migrate database to latest version...");
            flyway.migrate();
            LOGGER.info("did migrate database to latest version...");
        }
        else {
            LOGGER.warn("migration disabled");
        }
    }

}
