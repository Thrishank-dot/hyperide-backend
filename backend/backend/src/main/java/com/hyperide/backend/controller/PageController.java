package com.hyperide.backend.controller;

import com.hyperide.backend.model.User;
import com.hyperide.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse; // ADD THIS
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.regex.Pattern;

@Controller
public class PageController {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final Pattern PASS_PATTERN = Pattern.compile("^(?=.*[A-Z])(?=.*[!@#$&*])(?=.*[0-9]).{8,}$");

    // Map BOTH "/" and "/login" to this method
    @GetMapping({"/", "/login"})
    public String loginPage(@RequestParam(value = "logout", required = false) String logout, Model model) { 
        
        // If Spring Security appends "?logout" to the URL, show a success message!
        if (logout != null) {
            model.addAttribute("success", "You have been securely logged out.");
        }
        
        return "login"; 
    }

    @PostMapping("/doLogin")
    public String doLogin(@RequestParam String username, @RequestParam String password, HttpSession session, Model model) {
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isPresent() && passwordEncoder.matches(password, user.get().getPassword())) {
            session.setAttribute("user", user.get());
            return "redirect:/ide";
        }
        model.addAttribute("error", "Invalid credentials");
        return "login";
    }

    @PostMapping("/doRegister")
    public String doRegister(@RequestParam String username, @RequestParam String password, Model model) {
        if (userRepository.findByUsername(username).isPresent()) {
            model.addAttribute("error", "Username already exists!");
            return "login";
        }
        if (!PASS_PATTERN.matcher(password).matches()) {
            model.addAttribute("error", "Password must be 8+ chars, 1 Uppercase, 1 Number, 1 Special Char.");
            return "login";
        }
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setRole("USER");
        userRepository.save(newUser);
        model.addAttribute("success", "Registration successful! Please login.");
        return "login";
    }

    // --- SECURED IDE ROUTE ---
    @GetMapping("/ide")
    public String idePage(HttpSession session, Model model, HttpServletResponse response) {
        User user = (User) session.getAttribute("user");
        
        // If they aren't logged in, kick them back to login instantly
        if (user == null) return "redirect:/"; 

        // PREVENT BROWSER CACHING: Forces the browser to reload the page and verify the session
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        model.addAttribute("username", user.getUsername());
        model.addAttribute("role", user.getRole());
        return "ide";
    }

    // --- LOGOUT ROUTE ---
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate(); // Destroys the session
        return "redirect:/";  // Redirects to loginPage ("/")
    }
}