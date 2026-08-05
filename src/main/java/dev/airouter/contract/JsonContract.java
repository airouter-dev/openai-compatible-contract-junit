package dev.airouter.contract;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.Objects;

/**
 * Deliberately small streaming checks for the JSON shapes needed by this
 * contract. It does not deserialize provider payloads into mutable models.
 */
final class JsonContract {
    private static final JsonFactory JSON = new JsonFactory();

    private JsonContract() {
    }

    static void requireModelsShape(byte[] body) throws ContractViolationException {
        Objects.requireNonNull(body, "body");
        try (JsonParser parser = JSON.createParser(body)) {
            requireStartObject(parser);
            boolean dataSeen = false;
            boolean idSeen = false;
            boolean closed = false;
            JsonToken token;
            while ((token = parser.nextToken()) != null && token != JsonToken.END_OBJECT) {
                requireField(parser, token);
                String field = parser.currentName();
                token = parser.nextToken();
                if ("data".equals(field)) {
                    dataSeen = true;
                    if (token != JsonToken.START_ARRAY) {
                        throw new ContractViolationException("models data must be an array");
                    }
                    while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                        if (token != JsonToken.START_OBJECT) {
                            throw new ContractViolationException("models data entries must be objects");
                        }
                        while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                            requireField(parser, token);
                            String entryField = parser.currentName();
                            token = parser.nextToken();
                            if ("id".equals(entryField)
                                    && token == JsonToken.VALUE_STRING
                                    && !parser.getText().isBlank()) {
                                idSeen = true;
                            }
                            parser.skipChildren();
                        }
                    }
                } else {
                    parser.skipChildren();
                }
            }
            closed = token == JsonToken.END_OBJECT;
            if (!closed || !dataSeen || !idSeen || parser.nextToken() != null) {
                throw new ContractViolationException("models JSON must contain data with a non-empty id");
            }
        } catch (IOException | IllegalStateException exception) {
            throw new ContractViolationException("models response is not valid JSON", exception);
        }
    }

    static void requireTopLevelArray(byte[] body, String field, String label)
            throws ContractViolationException {
        Objects.requireNonNull(body, "body");
        try (JsonParser parser = JSON.createParser(body)) {
            requireStartObject(parser);
            boolean found = false;
            boolean closed = false;
            JsonToken token;
            while ((token = parser.nextToken()) != null && token != JsonToken.END_OBJECT) {
                requireField(parser, token);
                String current = parser.currentName();
                token = parser.nextToken();
                if (field.equals(current)) {
                    found = token == JsonToken.START_ARRAY;
                }
                parser.skipChildren();
            }
            closed = token == JsonToken.END_OBJECT;
            if (!closed || !found || parser.nextToken() != null) {
                throw new ContractViolationException(label + " JSON must contain a " + field + " array");
            }
        } catch (IOException | IllegalStateException exception) {
            throw new ContractViolationException(label + " response is not valid JSON", exception);
        }
    }

    static boolean hasTopLevelField(byte[] body, String field) {
        try (JsonParser parser = JSON.createParser(body)) {
            requireStartObject(parser);
            boolean found = false;
            JsonToken token;
            while ((token = parser.nextToken()) != null && token != JsonToken.END_OBJECT) {
                if (token != JsonToken.FIELD_NAME) {
                    return false;
                }
                String current = parser.currentName();
                parser.nextToken();
                if (field.equals(current)) {
                    found = true;
                }
                parser.skipChildren();
            }
            return token == JsonToken.END_OBJECT
                    && parser.nextToken() == null
                    && found;
        } catch (IOException | IllegalStateException | ContractViolationException exception) {
            return false;
        }
    }

    static boolean isValidJson(byte[] body) {
        try (JsonParser parser = JSON.createParser(body)) {
            if (parser.nextToken() == null) {
                return false;
            }
            parser.skipChildren();
            return parser.nextToken() == null;
        } catch (IOException | IllegalStateException exception) {
            return false;
        }
    }

    private static void requireStartObject(JsonParser parser)
            throws IOException, ContractViolationException {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new ContractViolationException("JSON response must be an object");
        }
    }

    private static void requireField(JsonParser parser, JsonToken token)
            throws ContractViolationException {
        if (token != JsonToken.FIELD_NAME) {
            throw new ContractViolationException("JSON object contains an invalid field");
        }
    }
}
