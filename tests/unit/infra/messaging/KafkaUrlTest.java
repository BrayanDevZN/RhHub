package br.rhhub.infra.messaging;

import br.rhhub.testing.UnitTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

@UnitTest
class KafkaUrlTest {

    @Test
    void noneEmptyOrMissingMeansLocalKafka() {
        for (String raw : new String[] {"none", "NONE", " None ", "", "   ", null, "null"}) {
            assertThat(KafkaUrl.parse(raw).bootstrapServers()).as("[%s]", raw).isEqualTo("localhost:9092");
        }
    }

    @Test
    void acceptsHostPortSchemesAndLists() {
        assertThat(KafkaUrl.parse("kafka.railway.internal:9092").bootstrapServers()).isEqualTo("kafka.railway.internal:9092");
        assertThat(KafkaUrl.parse("meu-kafka").bootstrapServers()).isEqualTo("meu-kafka:9092");
        assertThat(KafkaUrl.parse("kafka://h:9093/").bootstrapServers()).isEqualTo("h:9093");
        assertThat(KafkaUrl.parse("SASL_PLAINTEXT://h:9092").bootstrapServers()).isEqualTo("h:9092");
        assertThat(KafkaUrl.parse(" a:1 , PLAINTEXT://b , c:3 ").bootstrapServers()).isEqualTo("a:1,b:9092,c:3");
    }

    @Test
    void rejectsInvalidAddresses() {
        assertThatThrownBy(() -> KafkaUrl.parse("host:abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KafkaUrl.parse("host:0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KafkaUrl.parse("host:70000")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KafkaUrl.parse(":9092")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KafkaUrl.parse(" , ")).isInstanceOf(IllegalArgumentException.class);
    }
}
