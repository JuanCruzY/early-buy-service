package com.juancruzy.earlybuyservice.auth.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthUserResponse(String id, List<String> permissions, Boolean enabled) {
}
