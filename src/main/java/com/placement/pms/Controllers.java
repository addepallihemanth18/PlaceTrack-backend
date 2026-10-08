package com.placement.pms;

import jakarta.validation.*;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    final PlacementService service;
    final AuthenticationManager auth;
    final JwtService jwt;

    AuthController(PlacementService s, AuthenticationManager a, JwtService j) {
        service = s;
        auth = a;
        jwt = j;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody RegisterRequest r) {
        Student s = service.register(r);
        return new AuthResponse(jwt.create(s.user.email, s.user.role), s.user.role.name(), s.fullName);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest r) {
        String email = r.email().trim().toLowerCase();
        auth.authenticate(new UsernamePasswordAuthenticationToken(email, r.password()));
        User u = service.users().findByEmail(email).orElseThrow();
        String name = u.role == Role.ROLE_STUDENT ? service.current(u.email).fullName : "Placement Admin";
        return new AuthResponse(jwt.create(u.email, u.role), u.role.name(), name);
    }
}

@RestController
@RequestMapping("/api/students")
@Transactional
class StudentController {
    final PlacementService s;

    StudentController(PlacementService s) {
        this.s = s;
    }

    @GetMapping("/profile")
    Map<String, Object> profile(Authentication a) {
        return s.studentView(s.current(a.getName()));
    }

    @PutMapping("/profile")
    Map<String, Object> update(Authentication a, @RequestBody StudentRequest r) {
        return s.studentView(s.updateStudent(a.getName(), r));
    }

    @GetMapping("/dashboard")
    Map<String, Object> dashboard(Authentication a) {
        Student st = s.current(a.getName());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("student", s.studentView(st));
        m.put("availableDrives",
                s.drives().findByActiveTrue().stream().filter(d -> s.eligibility(st, d).eligible()).count());
        List<Application> apps = s.applications().findByStudentUserEmailOrderByAppliedDateDesc(a.getName());
        m.put("applications", apps.size());
        m.put("shortlisted", apps.stream().filter(x -> x.status == ApplicationStatus.SHORTLISTED).count());
        m.put("selected", apps.stream().filter(x -> x.status == ApplicationStatus.SELECTED).count());
        return m;
    }
}

@RestController
@RequestMapping("/api/drives")
@Transactional
class DriveController {
    final PlacementService s;

    DriveController(PlacementService s) {
        this.s = s;
    }

    @GetMapping
    List<Map<String, Object>> list(@RequestParam(required = false) String search,
            @RequestParam(required = false) String location, @RequestParam(required = false) String jobType,
            @RequestParam(required = false) Boolean active) {
        return s.drives().findAll().stream().filter(d -> active == null || d.active == active)
                .filter(d -> search == null
                        || (d.company.name + " " + d.jobTitle).toLowerCase().contains(search.toLowerCase()))
                .filter(d -> location == null
                        || (d.jobLocation != null && d.jobLocation.toLowerCase().contains(location.toLowerCase())))
                .filter(d -> jobType == null || jobType.equalsIgnoreCase(d.jobType)).map(s::driveView).toList();
    }

    @GetMapping("/{id}")
    Map<String, Object> one(@PathVariable Long id) {
        return s.driveView(s.get(s.drives(), id, "Drive"));
    }

    @GetMapping("/{id}/eligibility")
    EligibilityResponse eligibility(@PathVariable Long id, Authentication a) {
        return s.eligibility(s.current(a.getName()), s.get(s.drives(), id, "Drive"));
    }
}

@RestController
@RequestMapping("/api/applications")
@Transactional
class ApplicationController {
    final PlacementService s;

    ApplicationController(PlacementService s) {
        this.s = s;
    }

    @PostMapping("/drives/{driveId}")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> apply(@PathVariable Long driveId, Authentication a) {
        return s.applicationView(s.apply(a.getName(), driveId));
    }

    @GetMapping("/my")
    List<Map<String, Object>> my(Authentication a) {
        return s.applications().findByStudentUserEmailOrderByAppliedDateDesc(a.getName()).stream().map(s::applicationView)
                .toList();
    }
}

@RestController
@RequestMapping("/api/admin")
@Transactional
class AdminController {
    final PlacementService s;

    AdminController(PlacementService s) {
        this.s = s;
    }

    @GetMapping("/dashboard")
    Map<String, Object> dashboard() {
        return s.dashboard();
    }

    @GetMapping("/students")
    List<Map<String, Object>> students() {
        return s.students().findAll().stream().map(s::studentView).toList();
    }

    @GetMapping("/companies")
    List<Map<String, Object>> companies() {
        return s.companies().findAll().stream().map(s::companyView).toList();
    }

    @PostMapping("/companies")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> addCompany(@Valid @RequestBody CompanyRequest r) {
        return s.companyView(s.company(r));
    }

    @PutMapping("/companies/{id}")
    Map<String, Object> updateCompany(@PathVariable Long id, @Valid @RequestBody CompanyRequest r) {
        Company c = s.get(s.companies(), id, "Company");
        s.copy(c, r);
        return s.companyView(s.companies().save(c));
    }

    @DeleteMapping("/companies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCompany(@PathVariable Long id) {
        s.deleteCompany(id);
    }

    @PostMapping("/drives")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> addDrive(@Valid @RequestBody DriveRequest r) {
        return s.driveView(s.drive(r));
    }

    @PutMapping("/drives/{id}")
    Map<String, Object> updateDrive(@PathVariable Long id, @Valid @RequestBody DriveRequest r) {
        PlacementDrive d = s.get(s.drives(), id, "Drive");
        s.copy(d, r);
        return s.driveView(s.drives().save(d));
    }

    @DeleteMapping("/drives/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDrive(@PathVariable Long id) {
        s.deleteDrive(id);
    }

    @GetMapping("/applications")
    List<Map<String, Object>> applications() {
        return s.applications().findAll().stream().map(s::applicationView).toList();
    }

    @GetMapping("/drives/{id}/applications")
    List<Map<String, Object>> applicants(@PathVariable Long id) {
        return s.applications().findByDriveId(id).stream().map(s::applicationView).toList();
    }

    @PutMapping("/applications/{id}/status")
    Map<String, Object> status(@PathVariable Long id, @Valid @RequestBody StatusRequest r) {
        Application a = s.get(s.applications(), id, "Application");
        a.status = r.status();
        return s.applicationView(s.applications().save(a));
    }
}

@RestControllerAdvice
class Errors {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Errors.class);

    private static ResponseEntity<Map<String, String>> body(HttpStatusCode code, String message) {
        return ResponseEntity.status(code).body(Map.of("message", message));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(org.springframework.web.server.ResponseStatusException e) {
        return body(e.getStatusCode(), e.getReason() == null ? "Request failed" : e.getReason());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, String>> badLogin(AuthenticationException e) {
        return body(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(org.springframework.web.bind.MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> integrity(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        return body(HttpStatus.CONFLICT, "That change conflicts with existing data (duplicate value or record still in use).");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> generic(Exception e) {
        log.error("Request failed", e);
        return body(HttpStatus.BAD_REQUEST, e.getMessage() == null ? "Request failed" : e.getMessage());
    }
}
