package com.example.demo.config;

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

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "com.example.demo.mapper.korea", sqlSessionFactoryRef = "koreaSqlSessionFactory")
public class PtDbConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.korea")
    public DataSource koreaDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public SqlSessionFactory koreaSqlSessionFactory(@Qualifier("koreaDataSource") DataSource ds) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath:/mapper/korea/*.xml")
        );
        return factory.getObject();
    }

    @Bean
    public SqlSessionTemplate koreaSqlSessionTemplate(@Qualifier("koreaSqlSessionFactory") SqlSessionFactory sf) {
        return new SqlSessionTemplate(sf);
    }

    @Primary
    @Bean
    public DataSourceTransactionManager koreaTransactionManager(@Qualifier("koreaDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}