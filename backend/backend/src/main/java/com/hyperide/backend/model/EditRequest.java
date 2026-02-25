package com.hyperide.backend.model;
import lombok.Data;

@Data
public class EditRequest {
    private String fileName;
    private String content;
    private String user;
    private String role;
}