/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.context.refresher.type;

import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.context.refresher.MetaDataRefresher;
import org.apache.shardingsphere.infra.eventbus.ShardingSphereEventBus;
import org.apache.shardingsphere.infra.federation.optimizer.context.planner.OptimizerPlannerContext;
import org.apache.shardingsphere.infra.federation.optimizer.context.planner.OptimizerPlannerContextFactory;
import org.apache.shardingsphere.infra.federation.optimizer.metadata.FederationDatabaseMetaData;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.metadata.schema.builder.SchemaBuilderMaterials;
import org.apache.shardingsphere.infra.metadata.schema.builder.TableMetaDataBuilder;
import org.apache.shardingsphere.infra.metadata.schema.event.SchemaAlteredEvent;
import org.apache.shardingsphere.infra.metadata.schema.model.SchemaMetaData;
import org.apache.shardingsphere.infra.metadata.schema.model.TableMetaData;
import org.apache.shardingsphere.infra.rule.identifier.type.DataNodeContainedRule;
import org.apache.shardingsphere.infra.rule.identifier.type.MutableDataNodeRule;
import org.apache.shardingsphere.sql.parser.sql.common.statement.ddl.CreateTableStatement;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Schema refresher for create table statement.
 */
public final class CreateTableStatementSchemaRefresher implements MetaDataRefresher<CreateTableStatement> {
    
    private static final String TYPE = CreateTableStatement.class.getName();
    
    @Override
    public void refresh(final ShardingSphereMetaData metaData, final FederationDatabaseMetaData database, final Map<String, OptimizerPlannerContext> optimizerPlanners,
                        final Collection<String> logicDataSourceNames, final String schemaName, final CreateTableStatement sqlStatement, final ConfigurationProperties props) throws SQLException {
        String tableName = sqlStatement.getTable().getTableName().getIdentifier().getValue();
        if (!containsInImmutableDataNodeContainedRule(tableName, metaData)) {
            metaData.getRuleMetaData().findRules(MutableDataNodeRule.class).forEach(each -> each.put(logicDataSourceNames.iterator().next(), schemaName, tableName));
        }
        SchemaBuilderMaterials materials = new SchemaBuilderMaterials(metaData.getFrontendDatabaseType(),
                metaData.getResource().getDatabaseType(), metaData.getResource().getDataSources(), metaData.getRuleMetaData().getRules(), props, schemaName);
        Map<String, SchemaMetaData> metaDataMap = TableMetaDataBuilder.load(Collections.singletonList(tableName), materials);
        Optional<TableMetaData> actualTableMetaData = Optional.ofNullable(metaDataMap.get(schemaName)).map(optional -> optional.getTables().get(tableName));
        actualTableMetaData.ifPresent(optional -> {
            metaData.getSchemaByName(schemaName).put(tableName, optional);
            database.putTableMetadata(schemaName, optional);
            optimizerPlanners.put(database.getName(), OptimizerPlannerContextFactory.create(database));
            SchemaAlteredEvent event = new SchemaAlteredEvent(metaData.getDatabase().getName(), schemaName);
            event.getAlteredTables().add(optional);
            ShardingSphereEventBus.getInstance().post(event);
        });
    }
    
    private boolean containsInImmutableDataNodeContainedRule(final String tableName, final ShardingSphereMetaData metaData) {
        return metaData.getRuleMetaData().findRules(DataNodeContainedRule.class).stream()
                .filter(each -> !(each instanceof MutableDataNodeRule)).anyMatch(each -> each.getAllTables().contains(tableName));
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
}
