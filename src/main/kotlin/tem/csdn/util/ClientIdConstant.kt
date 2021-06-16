package tem.csdn.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import tem.csdn.model.ClientId

class ClientIdSerializer : JsonSerializer<ClientId>() {
    override fun serialize(
        clientId: ClientId,
        jsonGenerator: JsonGenerator,
        serializerProvider: SerializerProvider
    ) {
        jsonGenerator.writeString(clientId.value)
    }
}

class ClientIdDeserializer : JsonDeserializer<ClientId>() {
    override fun deserialize(jsonParser: JsonParser, deserializationContext: DeserializationContext): ClientId {
        return ClientId(jsonParser.text.trim())
    }
}