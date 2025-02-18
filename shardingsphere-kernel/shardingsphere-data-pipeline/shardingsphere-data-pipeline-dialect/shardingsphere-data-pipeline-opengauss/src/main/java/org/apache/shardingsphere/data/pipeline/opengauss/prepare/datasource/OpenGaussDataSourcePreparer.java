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

package org.apache.shardingsphere.data.pipeline.opengauss.prepare.datasource;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.data.pipeline.api.config.TableNameSchemaNameMapping;
import org.apache.shardingsphere.data.pipeline.api.datanode.JobDataNodeEntry;
import org.apache.shardingsphere.data.pipeline.api.datasource.PipelineDataSourceWrapper;
import org.apache.shardingsphere.data.pipeline.api.datasource.config.PipelineDataSourceConfigurationFactory;
import org.apache.shardingsphere.data.pipeline.api.datasource.config.impl.ShardingSpherePipelineDataSourceConfiguration;
import org.apache.shardingsphere.data.pipeline.api.prepare.datasource.ActualTableDefinition;
import org.apache.shardingsphere.data.pipeline.api.prepare.datasource.TableDefinitionSQLType;
import org.apache.shardingsphere.data.pipeline.core.datasource.PipelineDataSourceManager;
import org.apache.shardingsphere.data.pipeline.core.exception.PipelineJobPrepareFailedException;
import org.apache.shardingsphere.data.pipeline.core.prepare.datasource.AbstractDataSourcePreparer;
import org.apache.shardingsphere.data.pipeline.core.prepare.datasource.PrepareTargetTablesParameter;
import org.apache.shardingsphere.infra.datanode.DataNode;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Data source preparer for openGauss.
 */
@Slf4j
public final class OpenGaussDataSourcePreparer extends AbstractDataSourcePreparer {
    
    private static final String WITH_OF_TABLE_EXTEND = "with (";
    
    @Override
    public void prepareTargetTables(final PrepareTargetTablesParameter parameter) {
        Collection<ActualTableDefinition> actualTableDefinitions;
        try {
            actualTableDefinitions = getActualTableDefinitions(parameter);
        } catch (final SQLException ex) {
            throw new PipelineJobPrepareFailedException("get table definitions failed.", ex);
        }
        Map<String, Collection<String>> createLogicTableSQLs = getCreateLogicTableSQLs(actualTableDefinitions, parameter.getTableNameSchemaNameMapping());
        try (Connection targetConnection = getTargetCachedDataSource(parameter.getTaskConfig(), parameter.getDataSourceManager()).getConnection()) {
            for (Entry<String, Collection<String>> entry : createLogicTableSQLs.entrySet()) {
                for (String each : entry.getValue()) {
                    executeTargetTableSQL(targetConnection, each);
                }
                log.info("create target table '{}' success", entry.getKey());
            }
        } catch (final SQLException ex) {
            throw new PipelineJobPrepareFailedException("prepare target tables failed.", ex);
        }
    }
    
    private Collection<ActualTableDefinition> getActualTableDefinitions(final PrepareTargetTablesParameter parameter) throws SQLException {
        Collection<ActualTableDefinition> result = new LinkedList<>();
        ShardingSpherePipelineDataSourceConfiguration sourceDataSourceConfig = (ShardingSpherePipelineDataSourceConfiguration) PipelineDataSourceConfigurationFactory.newInstance(
                parameter.getJobConfig().getSource().getType(), parameter.getJobConfig().getSource().getParameter());
        // TODO reuse PipelineDataSourceManager
        try (PipelineDataSourceManager dataSourceManager = new PipelineDataSourceManager()) {
            for (JobDataNodeEntry each : parameter.getTablesFirstDataNodes().getEntries()) {
                DataNode dataNode = each.getDataNodes().get(0);
                // Keep dataSource to reuse
                PipelineDataSourceWrapper dataSource = dataSourceManager.getDataSource(sourceDataSourceConfig.getActualDataSourceConfig(dataNode.getDataSourceName()));
                try (Connection sourceConnection = dataSource.getConnection()) {
                    String schemaName = parameter.getTableNameSchemaNameMapping().getSchemaName(each.getLogicTableName());
                    String actualTableName = dataNode.getTableName();
                    String tableDefinition = queryTableDefinition(sourceConnection, schemaName, actualTableName);
                    log.info("getActualTableDefinitions, schemaName={}, dataNode={}, tableDefinition={}", schemaName, dataNode, tableDefinition);
                    String logicTableName = each.getLogicTableName();
                    result.add(new ActualTableDefinition(logicTableName, actualTableName, tableDefinition));
                }
            }
        }
        return result;
    }
    
    private String queryTableDefinition(final Connection sourceConnection, final String schemaName, final String actualTableName) throws SQLException {
        String sql = String.format("SELECT * FROM pg_get_tabledef('%s.%s'::regclass::oid)", schemaName, actualTableName);
        log.info("queryTableDefinition, sql={}", sql);
        try (Statement statement = sourceConnection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new PipelineJobPrepareFailedException("table definition has no result, sql: " + sql);
            }
            return resultSet.getString(1);
        }
    }
    
    /**
     * Get create logic table SQLs.
     *
     * @param actualTableDefinitions actual table definitions. key is logic table name, value is actual table definition.
     * @return all SQLs. key is logic table name, value is collection of logic table SQLs.
     */
    private Map<String, Collection<String>> getCreateLogicTableSQLs(final Collection<ActualTableDefinition> actualTableDefinitions, final TableNameSchemaNameMapping tableNameSchemaNameMapping) {
        Map<String, Collection<String>> result = new HashMap<>();
        for (ActualTableDefinition each : actualTableDefinitions) {
            String schemaName = tableNameSchemaNameMapping.getSchemaName(each.getLogicTableName());
            Collection<String> logicTableSQLs = splitTableDefinitionToSQLs(each).stream().map(sql -> {
                TableDefinitionSQLType sqlType = getTableDefinitionSQLType(sql);
                switch (sqlType) {
                    // TODO replace constraint and index name
                    case CREATE_TABLE:
                        sql = addIfNotExistsForCreateTableSQL(sql);
                        sql = appendSchemaName(sql, each.getActualTableName(), schemaName);
                        sql = replaceActualTableNameToLogicTableName(sql, each.getActualTableName(), each.getLogicTableName());
                        sql = skipCreateTableExtendSet(sql);
                        return sql;
                    case ALTER_TABLE:
                        sql = appendSchemaName(sql, each.getActualTableName(), schemaName);
                        return replaceActualTableNameToLogicTableName(sql, each.getActualTableName(), each.getLogicTableName());
                    default:
                        return "";
                }
            }).filter(sql -> !"".equals(sql)).collect(Collectors.toList());
            result.put(each.getLogicTableName(), logicTableSQLs);
        }
        return result;
    }
    
    private String appendSchemaName(final String createOrAlterTableSQL, final String actualTableName, final String schemaName) {
        int start = createOrAlterTableSQL.indexOf(actualTableName);
        if (start <= 0) {
            return createOrAlterTableSQL;
        }
        return new StringBuilder(createOrAlterTableSQL).insert(start, schemaName + ".").toString();
    }
    
    private String skipCreateTableExtendSet(final String createSQL) {
        String lowerCreateSQL = createSQL.toLowerCase();
        String[] search = {WITH_OF_TABLE_EXTEND, ")"};
        List<Integer> searchPos = new ArrayList<>(2);
        int startPos = 0;
        for (String each : search) {
            int curSearch = lowerCreateSQL.indexOf(each, startPos);
            if (curSearch <= 0) {
                break;
            }
            searchPos.add(curSearch);
            startPos = curSearch;
        }
        if (searchPos.size() != search.length) {
            return createSQL;
        }
        return createSQL.substring(0, searchPos.get(0)) + createSQL.substring(searchPos.get(1) + 1);
    }
}
