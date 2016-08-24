/*
 * Copyright 2015-2016 http://hsweb.me
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.hsweb.web.service.impl.datasource;

import org.hsweb.ezorm.executor.AbstractJdbcSqlExecutor;
import org.hsweb.ezorm.render.support.simple.SimpleSQL;
import org.hsweb.web.bean.common.QueryParam;
import org.hsweb.web.bean.common.UpdateMapParam;
import org.hsweb.web.bean.common.UpdateParam;
import org.hsweb.web.bean.po.datasource.DataSource;
import org.hsweb.web.core.exception.BusinessException;
import org.hsweb.web.dao.datasource.DataSourceMapper;
import org.hsweb.web.service.config.ConfigService;
import org.hsweb.web.service.impl.AbstractServiceImpl;
import org.hsweb.web.service.datasource.DataSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * 数据源服务类
 * Created by generator
 */
@Service("dataSourceService")
public class DataSourceServiceImpl extends AbstractServiceImpl<DataSource, String> implements DataSourceService {

    private static final String CACHE_NAME = "datasource";

    @Resource
    protected DataSourceMapper dataSourceMapper;

    @Resource
    protected ConfigService configService;

    @Autowired
    protected javax.sql.DataSource defaultDataSource;

    @Override
    protected DataSourceMapper getMapper() {
        return this.dataSourceMapper;
    }

    @Override
    public javax.sql.DataSource createDataSource(String id) {
        DataSource dataSource = selectByPk(id);
        DataSourceBuilder builder = DataSourceBuilder.create()
                .driverClassName(dataSource.getDriver())
                .url(dataSource.getUrl())
                .username(dataSource.getUsername())
                .password(dataSource.getPassword())
                .type(defaultDataSource.getClass());
        return builder.build();
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'id:'+#id")
    public DataSource selectByPk(String id) {
        return super.selectByPk(id);
    }

    @Override
    public String insert(DataSource data) {
        data.setCreateDate(new Date());
        data.setEnabled(1);
        tryValidPo(data);
        DataSource old = selectSingle(QueryParam.build().where("name", data.getName()));
        if (old != null) throw new BusinessException("名称" + data.getName() + "已存在");
        return super.insert(data);
    }

    @Override
    @CacheEvict(allEntries = true)
    public int update(List<DataSource> data) {
        return super.update(data);
    }

    @Override
    @CacheEvict(value = CACHE_NAME, key = "'id:'+#data.id")
    public int update(DataSource data) {
        DataSource old = selectSingle(QueryParam.build()
                .where("name", data.getName())
                .and("id$not", data.getId()));
        if (old != null) throw new BusinessException("名称" + data.getName() + "已存在");
        return getMapper().update(UpdateParam.build(data).excludes("createDate", "enabled"));
    }

    @Override
    @CacheEvict(value = CACHE_NAME, key = "'id:'+#id")
    public void enable(String id) {
        getMapper().update((UpdateParam) UpdateMapParam.build().set("enabled", 1).where("id", id));
    }

    @Override
    @CacheEvict(value = CACHE_NAME, key = "'id:'+#id")
    public void disable(String id) {
        getMapper().update((UpdateParam) UpdateMapParam.build().set("enabled", 0).where("id", id));
    }


    @Override
    @CacheEvict(value = CACHE_NAME, key = "'id:'+#id")
    public int delete(String id) {
        return super.delete(id);
    }

    @Override
    public boolean testConnection(String id) {
        DataSource old = selectByPk(id);
        assertNotNull(old, "数据源不存在");
        javax.sql.DataSource dataSource = createDataSource(id);
        AbstractJdbcSqlExecutor sqlExecutor = new AbstractJdbcSqlExecutor() {
            @Override
            public Connection getConnection() {
                return DataSourceUtils.getConnection(dataSource);
            }

            @Override
            public void releaseConnection(Connection connection) throws SQLException {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        };
        try {
            sqlExecutor.exec(new SimpleSQL(old.getTestSql()));
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}