package com.portfolio.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class ApiController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ApiController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @PostConstruct
    void seedData() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        saveUser(user("a1", "admin", "Admin User", "admin@college.edu", "admin123", "AU", null, null));
        saveUser(user("f1", "faculty", "Dr. Sarah Johnson", "sarah@college.edu", "faculty123", "SJ", null, "Computer Science"));
        saveUser(user("f2", "faculty", "Prof. Mark Lee", "mark@college.edu", "faculty123", "ML", null, "Software Engineering"));
        saveUser(user("s1", "student", "Arjun Sharma", "arjun@student.edu", "student123", "AS", "CS2021001", null));
        saveUser(user("s2", "student", "Priya Patel", "priya@student.edu", "student123", "PP", "CS2021002", null));
        saveUser(user("s3", "student", "Ravi Kumar", "ravi@student.edu", "student123", "RK", "CS2021003", null));

        saveProject(project("p1", "s1", "AI Chatbot System",
                "A conversational AI using NLP to handle customer queries automatically.",
                "Python, TensorFlow, Flask", "evaluated", "2024-12-10T10:00:00Z",
                "chatbot_report.pdf",
                evaluation(88, "Excellent implementation of NLP concepts. The UI could be improved.",
                        rubric(18, 22, 16, 15, 17), "f1", "2024-12-15T09:00:00Z")));
        saveProject(project("p2", "s2", "E-Commerce Platform",
                "Full-stack online store with cart, payment integration, and admin panel.",
                "React, Node.js, MongoDB", "submitted", "2024-12-12T14:00:00Z",
                "ecommerce_docs.zip", null));
        saveProject(project("p3", "s3", "IoT Smart Home Dashboard",
                "Real-time IoT sensor monitoring dashboard with alert notifications.",
                "React, MQTT, Raspberry Pi", "draft", null, null, null));
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        String email = text(body.get("email"));
        String password = text(body.get("password"));

        Optional<Map<String, Object>> found = findUserByEmail(email)
                .filter(u -> Objects.equals(text(u.get("password")), password));

        if (found.isEmpty()) {
            logError("Failed login attempt for " + (email.isBlank() ? "unknown email" : email));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password."));
        }

        Map<String, Object> safeUser = withoutPassword(found.get());
        return ResponseEntity.ok(Map.of("token", "demo-token-" + safeUser.get("id"), "user", safeUser));
    }

    @PostMapping("/register")
    ResponseEntity<?> register(@RequestBody Map<String, Object> body) {
        String name = text(body.get("name"));
        String email = text(body.get("email"));
        String password = text(body.get("password"));
        String role = text(body.getOrDefault("role", "student")).toLowerCase();

        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Name, email, and password are required."));
        }
        if (findUserByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Email already exists."));
        }

        String id = text(body.get("id"));
        if (id.isBlank()) {
            id = role.substring(0, 1) + System.currentTimeMillis();
        }

        Map<String, Object> newUser = new LinkedHashMap<>(body);
        newUser.put("id", id);
        newUser.put("role", role);
        newUser.put("avatar", text(body.get("avatar")).isBlank() ? initials(name) : text(body.get("avatar")));

        try {
            saveUser(newUser);
            return ResponseEntity.status(HttpStatus.CREATED).body(withoutPassword(newUser));
        } catch (DuplicateKeyException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Email already exists."));
        }
    }

    @GetMapping("/users")
    List<Map<String, Object>> getUsers() {
        return allUsers().stream()
                .map(this::withoutPassword)
                .sorted(Comparator.comparing(u -> text(u.get("name"))))
                .toList();
    }

    @DeleteMapping("/users/{id}")
    ResponseEntity<?> deleteUser(@PathVariable String id) {
        int deleted = jdbc.update("DELETE FROM app_users WHERE id = ?", id);
        if (deleted == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found."));
        }
        return ResponseEntity.ok(Map.of("message", "User removed."));
    }

    @GetMapping("/projects")
    List<Map<String, Object>> getProjects() {
        return allProjects().stream()
                .sorted(Comparator.comparing(p -> text(p.get("submittedAt")), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @GetMapping("/projects/student/{studentId}")
    List<Map<String, Object>> getStudentProjects(@PathVariable String studentId) {
        return allProjects().stream()
                .filter(p -> Objects.equals(text(p.get("studentId")), studentId))
                .sorted(Comparator.comparing(p -> text(p.get("submittedAt")), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @PostMapping("/projects")
    ResponseEntity<?> createProject(@RequestBody Map<String, Object> body) {
        Map<String, Object> project = normalizeProject(body);
        if (text(project.get("id")).isBlank()) {
            project.put("id", "p" + System.currentTimeMillis());
        }
        if (text(project.get("title")).isBlank() || text(project.get("studentId")).isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Project title and studentId are required."));
        }
        saveProject(project);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @PutMapping("/projects/{id}")
    ResponseEntity<?> updateProject(@PathVariable String id, @RequestBody Map<String, Object> body) {
        if (findProjectById(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Project not found."));
        }
        Map<String, Object> project = normalizeProject(body);
        project.put("id", id);
        saveProject(project);
        return ResponseEntity.ok(project);
    }

    @GetMapping("/errors")
    List<Map<String, Object>> getErrors() {
        return allErrors().stream()
                .sorted(Comparator.comparing(e -> text(e.get("timestamp")), Comparator.reverseOrder()))
                .toList();
    }

    @PostMapping("/errors")
    ResponseEntity<?> createError(@RequestBody Map<String, Object> body) {
        String message = text(body.get("message"));
        if (message.isBlank()) {
            message = "Unknown platform error";
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(logError(message));
    }

    @PutMapping("/errors/{id}/resolve")
    ResponseEntity<?> resolveError(@PathVariable String id) {
        Optional<Map<String, Object>> found = findErrorById(id);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Error not found."));
        }
        Map<String, Object> error = found.get();
        error.put("resolved", true);
        saveError(error);
        return ResponseEntity.ok(error);
    }

    @DeleteMapping("/errors/resolved")
    ResponseEntity<?> clearResolvedErrors() {
        jdbc.update("DELETE FROM error_logs WHERE resolved = TRUE");
        return ResponseEntity.ok(Map.of("message", "Resolved errors cleared."));
    }

    private List<Map<String, Object>> allUsers() {
        return jdbc.query("SELECT data FROM app_users", (rs, rowNum) -> fromJson(rs.getString("data")));
    }

    private Optional<Map<String, Object>> findUserByEmail(String email) {
        List<Map<String, Object>> result = jdbc.query("SELECT data FROM app_users WHERE email = ?",
                (rs, rowNum) -> fromJson(rs.getString("data")), email);
        return result.stream().findFirst();
    }

    private List<Map<String, Object>> allProjects() {
        return jdbc.query("SELECT data FROM projects", (rs, rowNum) -> fromJson(rs.getString("data")));
    }

    private Optional<Map<String, Object>> findProjectById(String id) {
        List<Map<String, Object>> result = jdbc.query("SELECT data FROM projects WHERE id = ?",
                (rs, rowNum) -> fromJson(rs.getString("data")), id);
        return result.stream().findFirst();
    }

    private List<Map<String, Object>> allErrors() {
        return jdbc.query("SELECT data FROM error_logs", (rs, rowNum) -> fromJson(rs.getString("data")));
    }

    private Optional<Map<String, Object>> findErrorById(String id) {
        List<Map<String, Object>> result = jdbc.query("SELECT data FROM error_logs WHERE id = ?",
                (rs, rowNum) -> fromJson(rs.getString("data")), id);
        return result.stream().findFirst();
    }

    private void saveUser(Map<String, Object> user) {
        jdbc.update("""
                INSERT INTO app_users (id, email, password, role, data)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE email = VALUES(email), password = VALUES(password), role = VALUES(role), data = VALUES(data)
                """, text(user.get("id")), text(user.get("email")), text(user.get("password")),
                text(user.get("role")), toJson(user));
    }

    private void saveProject(Map<String, Object> project) {
        jdbc.update("""
                INSERT INTO projects (id, student_id, status, submitted_at, data)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE student_id = VALUES(student_id), status = VALUES(status), submitted_at = VALUES(submitted_at), data = VALUES(data)
                """, text(project.get("id")), text(project.get("studentId")), text(project.get("status")),
                text(project.get("submittedAt")), toJson(project));
    }

    private void saveError(Map<String, Object> error) {
        jdbc.update("""
                INSERT INTO error_logs (id, resolved, timestamp, data)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE resolved = VALUES(resolved), timestamp = VALUES(timestamp), data = VALUES(data)
                """, text(error.get("id")), Boolean.TRUE.equals(error.get("resolved")),
                text(error.get("timestamp")), toJson(error));
    }

    private Map<String, Object> normalizeProject(Map<String, Object> raw) {
        Map<String, Object> project = new LinkedHashMap<>(raw);
        project.putIfAbsent("status", "submitted");
        project.putIfAbsent("submittedAt", Instant.now().toString());
        project.putIfAbsent("fileUrl", null);
        project.putIfAbsent("fileName", null);
        project.putIfAbsent("milestones", defaultMilestones());

        Object evaluationValue = project.get("evaluation");
        if (evaluationValue instanceof Map<?, ?> evaluation) {
            project.put("evaluation", normalizeEvaluation(evaluation));
        }
        return project;
    }

    private Map<String, Object> normalizeEvaluation(Map<?, ?> raw) {
        Map<String, Object> evaluation = new LinkedHashMap<>();
        raw.forEach((key, value) -> evaluation.put(String.valueOf(key), value));

        Object marks = evaluation.get("marks");
        if (marks instanceof Map<?, ?> marksMap) {
            Map<String, Object> rubric = new LinkedHashMap<>();
            marksMap.forEach((key, value) -> rubric.put(String.valueOf(key), toInt(value)));
            evaluation.put("rubric", rubric);
            evaluation.put("marks", rubric.values().stream().mapToInt(this::toInt).sum());
        } else {
            evaluation.put("marks", toInt(marks));
            evaluation.putIfAbsent("rubric", rubric(0, 0, 0, 0, 0));
        }

        evaluation.putIfAbsent("feedback", "");
        evaluation.putIfAbsent("evaluatedBy", "");
        evaluation.putIfAbsent("evaluatedAt", Instant.now().toString());
        return evaluation;
    }

    private Map<String, Object> user(String id, String role, String name, String email, String password,
                                     String avatar, String rollNo, String department) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("role", role);
        user.put("name", name);
        user.put("email", email);
        user.put("password", password);
        user.put("avatar", avatar);
        if (rollNo != null) {
            user.put("rollNo", rollNo);
        }
        if (department != null) {
            user.put("department", department);
        }
        return user;
    }

    private Map<String, Object> project(String id, String studentId, String title, String description,
                                        String tech, String status, String submittedAt, String fileName,
                                        Map<String, Object> evaluation) {
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("id", id);
        project.put("studentId", studentId);
        project.put("title", title);
        project.put("description", description);
        project.put("tech", tech);
        project.put("status", status);
        project.put("submittedAt", submittedAt);
        project.put("fileUrl", null);
        project.put("fileName", fileName);
        project.put("milestones", defaultMilestones());
        project.put("evaluation", evaluation);
        return project;
    }

    private Map<String, Object> evaluation(int marks, String feedback, Map<String, Object> rubric,
                                           String evaluatedBy, String evaluatedAt) {
        Map<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put("marks", marks);
        evaluation.put("feedback", feedback);
        evaluation.put("rubric", rubric);
        evaluation.put("evaluatedBy", evaluatedBy);
        evaluation.put("evaluatedAt", evaluatedAt);
        return evaluation;
    }

    private Map<String, Object> rubric(int innovation, int technical, int presentation, int documentation, int teamwork) {
        Map<String, Object> rubric = new LinkedHashMap<>();
        rubric.put("innovation", innovation);
        rubric.put("technical", technical);
        rubric.put("presentation", presentation);
        rubric.put("documentation", documentation);
        rubric.put("teamwork", teamwork);
        return rubric;
    }

    private List<Map<String, Object>> defaultMilestones() {
        List<Map<String, Object>> milestones = new ArrayList<>();
        milestones.add(milestone("Proposal", true));
        milestones.add(milestone("Design", true));
        milestones.add(milestone("Development", true));
        milestones.add(milestone("Testing", false));
        return milestones;
    }

    private Map<String, Object> milestone(String title, boolean done) {
        Map<String, Object> milestone = new LinkedHashMap<>();
        milestone.put("title", title);
        milestone.put("done", done);
        return milestone;
    }

    private Map<String, Object> logError(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("id", UUID.randomUUID().toString());
        error.put("message", message);
        error.put("timestamp", Instant.now().toString());
        error.put("resolved", false);
        saveError(error);
        return error;
    }

    private Map<String, Object> withoutPassword(Map<String, Object> source) {
        Map<String, Object> safe = new LinkedHashMap<>(source);
        safe.remove("password");
        return safe;
    }

    private String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder value = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                value.append(part.charAt(0));
            }
            if (value.length() == 2) {
                break;
            }
        }
        return value.toString().toUpperCase();
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read JSON from database", ex);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not write JSON to database", ex);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
