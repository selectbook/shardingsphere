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

package org.apache.shardingsphere.sharding.rewrite.fixture.encrypt;

import lombok.Getter;
import lombok.Setter;
import org.apache.shardingsphere.encrypt.spi.EncryptAlgorithm;
import org.apache.shardingsphere.encrypt.spi.context.EncryptContext;
import org.apache.shardingsphere.infra.metadata.schema.ShardingSphereSchema;
import org.apache.shardingsphere.infra.metadata.schema.model.TableMetaData;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.aware.SchemaMetaDataAware;

import java.util.Map;
import java.util.Properties;

public final class RewriteSchemaMetaDataAwareEncryptAlgorithmFixture implements EncryptAlgorithm<Object, String>, SchemaMetaDataAware {
    
    @Getter
    private Properties props;
    
    @Setter
    private String databaseName;
    
    @Setter
    private Map<String, ShardingSphereSchema> schemas;
    
    @Override
    public void init(final Properties props) {
        this.props = props;
    }
    
    @Override
    public String encrypt(final Object plainValue, final EncryptContext encryptContext) {
        return "encrypt_" + plainValue + "_" + schemas.get(databaseName).get(encryptContext.getTableName()).getName();
    }
    
    @Override
    public Object decrypt(final String cipherValue, final EncryptContext encryptContext) {
        TableMetaData tableMetaData = schemas.get(databaseName).get(encryptContext.getTableName());
        return cipherValue.replaceAll("encrypt_", "").replaceAll("_" + tableMetaData.getName(), "");
    }
    
    @Override
    public String getType() {
        return "REWRITE.METADATA_AWARE.FIXTURE";
    }
}
