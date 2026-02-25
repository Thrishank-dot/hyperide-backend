package com.hyperide.backend.controller;

import com.hyperide.backend.model.ChatMessage;
import com.hyperide.backend.model.EditRequest;
import com.hyperide.backend.model.EditResponse;
import com.hyperide.backend.model.User;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class EditorController {

    private static final Path STORAGE_ROOT = Paths.get("hyperide_files").toAbsolutePath().normalize();
    
    private static final Map<String, String> memoryCache = new ConcurrentHashMap<>();
    private static final Map<String, String> editLocks = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> userContributions = new ConcurrentHashMap<>();
    private static final Map<String, String> userPresence = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(STORAGE_ROOT.resolve("admin"));
            saveFileToDiskSync("admin/welcome.txt", "Welcome to the Admin Dashboard!\n// System operational.");
        } catch (IOException e) { System.err.println("Failed to init storage."); }
    }

    private void saveFileToDiskSync(String requestedPath, String content) {
        try {
            requestedPath = requestedPath.replace("\\", "/");
            Path targetPath = STORAGE_ROOT.resolve(requestedPath).normalize();
            if (!targetPath.startsWith(STORAGE_ROOT)) return; 
            memoryCache.put(requestedPath, content);
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, content);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private String loadFile(String requestedPath) {
        if (memoryCache.containsKey(requestedPath)) return memoryCache.get(requestedPath);
        try {
            requestedPath = requestedPath.replace("\\", "/");
            Path targetPath = STORAGE_ROOT.resolve(requestedPath).normalize();
            if (!targetPath.startsWith(STORAGE_ROOT)) return "// Security Violation";
            if (Files.exists(targetPath)) {
                String content = Files.readString(targetPath);
                memoryCache.put(requestedPath, content);
                return content;
            }
        } catch (IOException e) { e.printStackTrace(); }
        return "";
    }

    // --- REST APIs ---
    @GetMapping("/api/files")
    @ResponseBody
    public List<String> getFiles(HttpSession session) { 
        User user = (User) session.getAttribute("user");
        String role = (user != null) ? user.getRole() : "USER";
        try (Stream<Path> walk = Files.walk(STORAGE_ROOT)) {
            return walk.filter(Files::isRegularFile)
                       .map(p -> STORAGE_ROOT.relativize(p).toString().replace("\\", "/"))
                       .filter(path -> "ADMIN".equalsIgnoreCase(role) || !path.startsWith("admin/"))
                       .collect(Collectors.toList());
        } catch (IOException e) { return new ArrayList<>(); }
    }

    @GetMapping("/api/editor/content")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> getContent(@RequestParam String path, HttpSession session) {
        User user = (User) session.getAttribute("user");
        String role = (user != null) ? user.getRole() : "USER";
        path = path.replace("\\", "/");
        if (path.startsWith("admin/") && !"ADMIN".equalsIgnoreCase(role)) {
            return org.springframework.http.ResponseEntity.status(403).body("// ERROR: ACCESS DENIED.");
        }
        return org.springframework.http.ResponseEntity.ok(loadFile(path));
    }

    @GetMapping("/api/stats")
    @ResponseBody
    public Map<String, Long> getStats() {
        // Simplified: Just returns raw edit counts for the pie chart
        return userContributions.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    // --- WEBSOCKETS ---
    @MessageMapping("/chat.send")
    @SendTo("/topic/public")
    public ChatMessage handleChat(ChatMessage message) {
        message.setTimestamp(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        return message;
    }

    @MessageMapping("/presence")
    @SendTo("/topic/presence")
    public Map<String, String> updatePresence(Map<String, String> payload) {
        userPresence.put(payload.get("user"), payload.get("file"));
        return userPresence;
    }

    @MessageMapping("/files.create")
    @SendTo("/topic/files")
    public String handleCreateFile(Map<String, String> payload) {
        String fileName = payload.get("name");
        String creator = payload.get("creator");
        String role = payload.get("role");
        if ("admin".equalsIgnoreCase(creator) && !"ADMIN".equalsIgnoreCase(role)) return "REJECTED"; 
        String fullPath = creator + "/" + fileName;
        if (!memoryCache.containsKey(fullPath)) saveFileToDiskSync(fullPath, "");
        return "UPDATE_NEEDED"; 
    }
    
    @MessageMapping("/files.delete")
    @SendTo("/topic/files")
    public String handleDeleteFile(Map<String, String> payload) {
        String path = payload.get("path");
        String role = payload.get("role");
        if (!"ADMIN".equalsIgnoreCase(role)) return "REJECTED";
        try {
            Path targetPath = STORAGE_ROOT.resolve(path).normalize();
            if (targetPath.startsWith(STORAGE_ROOT)) {
                Files.deleteIfExists(targetPath);
                memoryCache.remove(path);
                editLocks.remove(path);
            }
        } catch (IOException e) { e.printStackTrace(); }
        return "UPDATE_NEEDED";
    }

    @MessageMapping("/edit")
    @SendTo("/topic/updates")
    public EditResponse handleEdit(EditRequest request) {
        String path = request.getFileName().replace("\\", "/"); 
        String user = request.getUser();
        String role = request.getRole(); 
        String owner = editLocks.get(path);
        
        if (path.startsWith("admin/") && !"ADMIN".equalsIgnoreCase(role)) {
            return new EditResponse("ERROR", "Access Denied.", user, path);
        }
        if (owner != null && !owner.equals(user) && !"ADMIN".equalsIgnoreCase(role)) {
            return new EditResponse("LOCKED", "Locked by " + owner, user, path);
        }
        if (owner == null) editLocks.put(path, user);
        
        userContributions.computeIfAbsent(user, k -> new AtomicLong(0)).incrementAndGet();
        
        // SYNCHRONOUS FULL PAYLOAD SAVE
        saveFileToDiskSync(path, request.getContent());
        
        return new EditResponse("UPDATE", request.getContent(), user, path);
    }
    // --- SERVER-SIDE COMPILER PROXY ---
    // --- SERVER-SIDE COMPILER PROXY (STEALTH MODE) ---
    // --- LOCAL EXECUTION ENGINE (NO EXTERNAL API REQUIRED) ---
    @PostMapping("/api/run")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> runCodeLocal(@RequestBody Map<String, Object> payload) {
        try {
            String language = (String) payload.get("language");
            
            // Extract code from the JSON payload
            @SuppressWarnings("unchecked")
            List<Map<String, String>> files = (List<Map<String, String>>) payload.get("files");
            String code = files.get(0).get("content");
            String output = "";

            if ("java".equalsIgnoreCase(language)) {
                // Dynamically find the Java class name (defaults to "Main")
                String className = "Main";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("public\\s+class\\s+([a-zA-Z0-9_]+)").matcher(code);
                if (m.find()) { className = m.group(1); }

                Path tempDir = Files.createTempDirectory("hyperide_exec");
                Path sourceFile = tempDir.resolve(className + ".java");
                Files.writeString(sourceFile, code);

                // 1. Compile the Java code
                Process compile = new ProcessBuilder("javac", sourceFile.toString())
                        .redirectErrorStream(true).start();
                compile.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

                if (compile.exitValue() != 0) {
                    output = "COMPILE ERROR:\n" + new String(compile.getInputStream().readAllBytes());
                } else {
                    // 2. Run the compiled Java bytecode
                    Process run = new ProcessBuilder("java", "-cp", tempDir.toString(), className)
                            .redirectErrorStream(true).start();
                    run.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    output = new String(run.getInputStream().readAllBytes());
                }
            } else if ("python".equalsIgnoreCase(language)) {
                Path sourceFile = Files.createTempFile("script", ".py");
                Files.writeString(sourceFile, code);
                
                // Run the Python script natively
                Process run = new ProcessBuilder("python", sourceFile.toString())
                        .redirectErrorStream(true).start();
                run.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                output = new String(run.getInputStream().readAllBytes());
            } else {
                output = "Language not supported by local engine.";
            }

            // Package the output into the exact JSON format the frontend expects
            Map<String, Object> responseData = new HashMap<>();
            Map<String, String> runData = new HashMap<>();
            runData.put("output", output);
            responseData.put("run", runData);

            return org.springframework.http.ResponseEntity.ok(responseData);

        } catch (Exception e) {
            Map<String, Object> errData = new HashMap<>();
            Map<String, String> runData = new HashMap<>();
            runData.put("output", "Local Execution Error: " + e.getMessage());
            errData.put("run", runData);
            return org.springframework.http.ResponseEntity.status(500).body(errData);
        }
    }
}