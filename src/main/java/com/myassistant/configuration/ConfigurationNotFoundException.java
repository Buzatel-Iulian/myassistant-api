package com.myassistant.configuration;

import com.myassistant.shared.error.NotFoundException;

// Also thrown for another user's configuration: the answer must not reveal that it exists.
public class ConfigurationNotFoundException extends NotFoundException {

    public ConfigurationNotFoundException(String configurationId) {
        super("No configuration with id " + configurationId);
    }
}
