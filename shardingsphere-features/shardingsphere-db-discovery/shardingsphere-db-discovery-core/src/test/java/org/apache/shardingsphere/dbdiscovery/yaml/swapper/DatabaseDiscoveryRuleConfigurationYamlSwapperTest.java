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

package org.apache.shardingsphere.dbdiscovery.yaml.swapper;

import org.apache.shardingsphere.dbdiscovery.api.config.DatabaseDiscoveryRuleConfiguration;
import org.apache.shardingsphere.dbdiscovery.api.config.rule.DatabaseDiscoveryDataSourceRuleConfiguration;
import org.apache.shardingsphere.dbdiscovery.yaml.config.YamlDatabaseDiscoveryRuleConfiguration;
import org.apache.shardingsphere.dbdiscovery.yaml.config.rule.YamlDatabaseDiscoveryDataSourceRuleConfiguration;
import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithmConfiguration;
import org.apache.shardingsphere.infra.yaml.config.swapper.YamlRuleConfigurationSwapperFactory;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public final class DatabaseDiscoveryRuleConfigurationYamlSwapperTest {
    
    @Test
    public void assertSwapToYamlWithLoadBalanceAlgorithm() {
        DatabaseDiscoveryDataSourceRuleConfiguration dataSourceConfig =
                new DatabaseDiscoveryDataSourceRuleConfiguration("ds", Collections.singletonList("dataSourceName"), "ha_heartbeat", "discoveryTypeName");
        YamlDatabaseDiscoveryRuleConfiguration actual = getHARuleConfigurationYamlSwapper().swapToYamlConfiguration(new DatabaseDiscoveryRuleConfiguration(Collections.singleton(dataSourceConfig),
                Collections.emptyMap(), Collections.singletonMap("mgr", new ShardingSphereAlgorithmConfiguration("MySQL.MGR", new Properties()))));
        assertThat(actual.getDataSources().keySet(), is(Collections.singleton("ds")));
        assertThat(actual.getDataSources().get("ds").getDataSourceNames(), is(Collections.singletonList("dataSourceName")));
    }
    
    @Test
    public void assertSwapToYamlWithoutLoadBalanceAlgorithm() {
        DatabaseDiscoveryDataSourceRuleConfiguration dataSourceConfig = new DatabaseDiscoveryDataSourceRuleConfiguration("ds", Collections.singletonList("dataSourceName"),
                "ha_heartbeat", "discoveryTypeName");
        YamlDatabaseDiscoveryRuleConfiguration actual = getHARuleConfigurationYamlSwapper().swapToYamlConfiguration(
                new DatabaseDiscoveryRuleConfiguration(Collections.singleton(dataSourceConfig), Collections.emptyMap(), Collections.emptyMap()));
        assertThat(actual.getDataSources().keySet(), is(Collections.singleton("ds")));
        assertThat(actual.getDataSources().get("ds").getDataSourceNames(), is(Collections.singletonList("dataSourceName")));
    }
    
    @Test
    public void assertSwapToObjectWithLoadBalanceAlgorithmType() {
        YamlDatabaseDiscoveryRuleConfiguration yamlConfig = createYamlHARuleConfiguration();
        DatabaseDiscoveryRuleConfiguration actual = getHARuleConfigurationYamlSwapper().swapToObject(yamlConfig);
        assertHARuleConfiguration(actual);
    }
    
    @Test
    public void assertSwapToObjectWithoutLoadBalanceAlgorithm() {
        YamlDatabaseDiscoveryRuleConfiguration yamlConfig = createYamlHARuleConfiguration();
        DatabaseDiscoveryRuleConfiguration actual = getHARuleConfigurationYamlSwapper().swapToObject(yamlConfig);
        assertHARuleConfiguration(actual);
    }
    
    private YamlDatabaseDiscoveryRuleConfiguration createYamlHARuleConfiguration() {
        YamlDatabaseDiscoveryRuleConfiguration result = new YamlDatabaseDiscoveryRuleConfiguration();
        result.getDataSources().put("ha_ds", new YamlDatabaseDiscoveryDataSourceRuleConfiguration());
        result.getDataSources().get("ha_ds").setDataSourceNames(Arrays.asList("ds_0", "ds_1"));
        return result;
    }
    
    private void assertHARuleConfiguration(final DatabaseDiscoveryRuleConfiguration actual) {
        DatabaseDiscoveryDataSourceRuleConfiguration group = actual.getDataSources().iterator().next();
        assertThat(group.getGroupName(), is("ha_ds"));
        assertThat(group.getDataSourceNames(), is(Arrays.asList("ds_0", "ds_1")));
    }
    
    private DatabaseDiscoveryRuleConfigurationYamlSwapper getHARuleConfigurationYamlSwapper() {
        Optional<DatabaseDiscoveryRuleConfigurationYamlSwapper> optional = YamlRuleConfigurationSwapperFactory.getAllInstances().stream()
                .filter(each -> each instanceof DatabaseDiscoveryRuleConfigurationYamlSwapper)
                .map(each -> (DatabaseDiscoveryRuleConfigurationYamlSwapper) each)
                .findFirst();
        assertTrue(optional.isPresent());
        return optional.get();
    }
}
