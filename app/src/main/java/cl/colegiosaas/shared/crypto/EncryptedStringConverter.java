package cl.colegiosaas.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Cifra al guardar y descifra al leer. Se usa así:
 * {@code @Convert(converter = EncryptedStringConverter.class) private String email;}
 * Hibernate lo crea a través de Spring, por eso puede recibir {@link FieldCipher}.
 * Las columnas cifradas no se pueden filtrar ni ordenar en SQL.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldCipher cipher;

    public EncryptedStringConverter(FieldCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
