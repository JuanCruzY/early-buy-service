package com.juancruzy.earlybuyservice.subscription.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;
import com.juancruzy.earlybuyservice.subscription.model.NotificationChannel;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.springframework.stereotype.Component;

@Component
public class ContactValidator {
    private final Validator validator;
    private final PhoneNumberUtil phones = PhoneNumberUtil.getInstance();

    public ContactValidator(Validator validator) {
        this.validator = validator;
    }

    public String validateAndNormalize(NotificationChannel channel, String contact) {
        if (contact == null || contact.isBlank()) {
            throw new ApplicationException(ErrorCode.MISSING_FIELDS);
        }
        return switch (channel) {
            case EMAIL -> validateEmail(contact.strip());
            case SMS -> validatePhone(contact.strip());
            case PUSH -> contact;
        };
    }

    private String validateEmail(String contact) {
        if (!validator.validate(new EmailContact(contact)).isEmpty()) {
            throw new ApplicationException(ErrorCode.INVALID_CONTACT);
        }
        return contact;
    }

    private String validatePhone(String contact) {
        // Restrict input before parsing: the library also accepts letters in vanity numbers.
        if (!contact.matches("\\+[0-9 ()-]+")) {
            throw new ApplicationException(ErrorCode.INVALID_CONTACT);
        }
        try {
            var number = phones.parse(contact, null);
            if (!phones.isValidNumber(number) || number.hasExtension()) {
                throw new ApplicationException(ErrorCode.INVALID_CONTACT);
            }
            return phones.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException exception) {
            throw new ApplicationException(ErrorCode.INVALID_CONTACT, exception);
        }
    }

    private record EmailContact(@Email String value) { }
}
