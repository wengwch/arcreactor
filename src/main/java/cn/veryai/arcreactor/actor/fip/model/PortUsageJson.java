package cn.veryai.arcreactor.actor.fip.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.BitSet;

/** Stable compact encoding for Pekko's Jackson/CBOR snapshots. */
public final class PortUsageJson {
  private PortUsageJson() {}

  public static final class Serializer extends JsonSerializer<BitSet> {
    @Override public void serialize(BitSet value, JsonGenerator generator, SerializerProvider provider) throws IOException {
      generator.writeBinary(value.toByteArray());
    }
  }

  public static final class Deserializer extends JsonDeserializer<BitSet> {
    @Override public BitSet deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      return BitSet.valueOf(parser.getBinaryValue());
    }
  }
}
