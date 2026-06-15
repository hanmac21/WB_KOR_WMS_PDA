package com.example.demo.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Configuration
@MapperScan(basePackages = "com.example.demo.mapper.mexico", sqlSessionFactoryRef = "mexicoSqlSessionFactory")
public class MexicoDbConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.mexico")
    public DataSource mexicoDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public SqlSessionFactory mexicoSqlSessionFactory(@Qualifier("mexicoDataSource") DataSource ds) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath:/mapper/mexico/*.xml")  
        );
        return factory.getObject();
    }

    @Bean
    public SqlSessionTemplate mexicoSqlSessionTemplate(@Qualifier("mexicoSqlSessionFactory") SqlSessionFactory sf) {
        return new SqlSessionTemplate(sf);
    }

    @Bean
    public DataSourceTransactionManager mexicoTransactionManager(@Qualifier("mexicoDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}
