package io.u11.skytrainsim.backend.config

import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.spring5.SpringConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class DatabaseConfig {
    @Bean
    fun jdbi(
        datasource: DataSource,
        mappers: List<RowMapper<*>>,
    ): Jdbi {
        val cf = SpringConnectionFactory(datasource)
        val jdbi = Jdbi.create(cf)
        jdbi.installPlugins()
        mappers.forEach {
            jdbi.registerRowMapper(it)
        }
        return jdbi
    }
}
