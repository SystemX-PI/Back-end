package com.systemX.senai;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "systemx.test.flyway.port", matches = "[0-9]+")
class FlywayMigrationIntegrationTest {
    @Test
    void migracoesEmBancoVazioCriamInnoDbIndicesEChaves() {
        var db = database("systemx_flyway_fresh");
        assertEmpty(db);
        var flyway = flyway(db, null);
        assertEquals(21, flyway.migrate().migrationsExecuted);
        flyway.validate();
        var sql = new JdbcTemplate(db);
        assertEquals("InnoDB", sql.queryForObject("""
                SELECT ENGINE FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name='baixa'
                """, String.class));
        assertEquals(3, count(sql, """
                SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                WHERE table_schema=DATABASE() AND (index_name IN ('ix_comunicacao_outbox','uk_comunicacao_chave_operacao')
                OR (table_name='baixa' AND column_name='processo_id' AND non_unique=0))
                """));
        assertEquals(2, count(sql, """
                SELECT COUNT(*) FROM information_schema.referential_constraints
                WHERE constraint_schema=DATABASE() AND constraint_name IN ('fk_baixa_processo','fk_comunicacao_original')
                """));
    }

    @Test
    void atualizacaoV19PreservaHistoricoEAssinaturaERecuperaSomenteDespachoSeguro() {
        var phase = System.getProperty("systemx.test.flyway.upgrade.phase", "all");
        var source = database("systemx_flyway_upgrade");
        switch (phase) {
            case "all" -> {
                prepareV19(source);
                verifyUpgrade(source);
            }
            case "snapshot" -> prepareV19(source);
            case "restore" -> verifyUpgrade(database("systemx_flyway_restored"));
            default -> fail("Fase de migração inválida.");
        }
    }

    private void prepareV19(DriverManagerDataSource db) {
        assertEmpty(db);
        assertEquals(19, flyway(db, "19").migrate().migrationsExecuted);
        var sql = new JdbcTemplate(db);
        long process = sql.queryForObject("SELECT MIN(id) FROM processo_contemplacao", Long.class);
        long operator = sql.queryForObject("SELECT MIN(id) FROM usuario", Long.class);
        sql.update("""
                INSERT INTO comunicacao (id,processo_id,canal,status,responsavel_id,comunicado_oficial,estado_despacho,erro)
                VALUES (8101,?,'EMAIL','PENDENTE',?,FALSE,'CONCLUIDO','Canal de e-mail indisponível.'),
                       (8102,?,'WHATSAPP','SEM_RESPOSTA',?,FALSE,'INCERTO',NULL),
                       (8103,?,'WHATSAPP','REALIZADA',?,TRUE,NULL,NULL)
                """, process, operator, process, operator, process, operator);
        sql.update("""
                INSERT INTO historico_comunicacao (comunicacao_id,data,canal,status,usuario_id,observacao)
                VALUES (8103,CURRENT_TIMESTAMP,'WHATSAPP','REALIZADA',?,'Histórico fictício')
                """, operator);
        sql.update("""
                UPDATE platform_account SET stripe_customer_id='cus_fixture', stripe_subscription_id='sub_fixture',
                    stripe_subscription_status='active', professional=TRUE, paid_until='2030-01-01 12:00:00'
                WHERE internal_operations=TRUE
                """);
        sql.update("""
                INSERT INTO password_reset_rate_limit (bucket, hits, expires_at)
                VALUES (REPEAT('a', 64), 2, 1900000000)
                """);
    }

    private void verifyUpgrade(DriverManagerDataSource db) {
        var before = flyway(db, "19");
        assertEquals("19", before.info().current().getVersion().getVersion());
        var sql = new JdbcTemplate(db);
        assertEquals("INCERTO", sql.queryForObject("SELECT estado_despacho FROM comunicacao WHERE id=8102", String.class));
        var current = flyway(db, null);
        assertEquals(2, current.migrate().migrationsExecuted);
        current.validate();
        assertEquals("NA_FILA", sql.queryForObject("SELECT estado_despacho FROM comunicacao WHERE id=8101", String.class));
        assertEquals("INCERTO_EXECUCAO", sql.queryForObject("SELECT estado_despacho FROM comunicacao WHERE id=8102", String.class));
        assertNull(sql.queryForObject("SELECT estado_despacho FROM comunicacao WHERE id=8103", String.class));
        assertEquals(1, count(sql, "SELECT COUNT(*) FROM historico_comunicacao WHERE comunicacao_id=8103"));
        assertEquals(1, count(sql, """
                SELECT COUNT(*) FROM password_reset_rate_limit
                WHERE bucket=REPEAT('a',64) AND hits=2 AND expires_at=1900000000
                """));
        assertEquals(1, count(sql, """
                SELECT COUNT(*) FROM platform_account WHERE stripe_customer_id='cus_fixture'
                AND stripe_subscription_id='sub_fixture' AND stripe_subscription_status='active'
                AND professional=TRUE AND paid_until='2030-01-01 12:00:00' AND stripe_checkout_attempt IS NULL
                """));
    }

    private DriverManagerDataSource database(String schema) {
        int port = Integer.parseInt(System.getProperty("systemx.test.flyway.port"));
        assertTrue(port > 1023 && port <= 65535, "Use uma porta MySQL de teste em loopback.");
        String password = System.getenv("SYSTEMX_LOTE03_MYSQL_PASSWORD");
        assertNotNull(password, "Configure a senha efêmera do MySQL de testes.");
        return new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:" + port + "/" + schema + "?allowPublicKeyRetrieval=true&sslMode=DISABLED",
                "systemx_lote03_test", password);
    }

    private void assertEmpty(DriverManagerDataSource db) {
        assertEquals(0, count(new JdbcTemplate(db),
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"),
                "Use um schema descartável vazio. O teste não limpa dados.");
    }

    private Flyway flyway(DriverManagerDataSource db, String target) {
        var config = Flyway.configure().dataSource(db).cleanDisabled(true);
        if (target != null) config.target(MigrationVersion.fromVersion(target));
        return config.load();
    }

    private int count(JdbcTemplate jdbc, String query) {
        return jdbc.queryForObject(query, Integer.class);
    }
}
