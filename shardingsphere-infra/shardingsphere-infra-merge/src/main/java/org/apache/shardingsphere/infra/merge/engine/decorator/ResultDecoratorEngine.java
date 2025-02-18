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

package org.apache.shardingsphere.infra.merge.engine.decorator;

import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.database.type.DatabaseType;
import org.apache.shardingsphere.infra.merge.engine.ResultProcessEngine;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;

/**
 * Result decorator engine.
 *
 * @param <T> type of rule
 */
public interface ResultDecoratorEngine<T extends ShardingSphereRule> extends ResultProcessEngine<T> {
    
    /**
     * Create new instance of result decorator.
     * 
     * @param databaseType database type
     * @param databaseName database name
     * @param metaData ShardingSphere metaData
     * @param rule rule
     * @param props ShardingSphere properties
     * @param sqlStatementContext SQL statement context
     * @return created instance
     */
    ResultDecorator<?> newInstance(DatabaseType databaseType, String databaseName, ShardingSphereMetaData metaData, T rule, ConfigurationProperties props, SQLStatementContext<?> sqlStatementContext);
}
