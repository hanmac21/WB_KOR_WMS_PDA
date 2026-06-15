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
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Configuration
@MapperScan(basePackages = "com.example.demo.mapper.usa", sqlSessionFactoryRef = "usaSqlSessionFactory")
public class UsaDbConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.usa")
    public DataSource usaDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public SqlSessionFactory usaSqlSessionFactory(@Qualifier("usaDataSource") DataSource ds) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath:/mapper/usa/*.xml")
        );
        return factory.getObject();
    }

    @Bean
    public SqlSessionTemplate usaSqlSessionTemplate(@Qualifier("usaSqlSessionFactory") SqlSessionFactory sf) {
        return new SqlSessionTemplate(sf);
    }

    @Primary
    @Bean
    public DataSourceTransactionManager usaTransactionManager(@Qualifier("usaDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}