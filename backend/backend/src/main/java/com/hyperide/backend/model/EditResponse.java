package com.hyperide.backend.model;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EditResponse {
    private String type;     // DELTA, ERROR, FULL
    private String content;  // Can hold a String or List<CodeDelta>
    private String user;
    private String fileName;
}