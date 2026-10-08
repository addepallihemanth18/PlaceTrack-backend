package com.placement.pms;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.*;
import org.springframework.security.core.*;
import org.springframework.security.crypto.password.*;
import org.springframework.stereotype.*;
import org.springframework.web.server.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

@Service
@Transactional
public class PlacementService {
    @Autowired UserRepository users;
    @Autowired StudentRepository students;
    @Autowired SkillRepository skills;
    @Autowired CompanyRepository companies;
    @Autowired DriveRepository drives;
    @Autowired ApplicationRepository applications;
    @Autowired PasswordEncoder encoder;

    // Controllers must go through these methods. This class is @Transactional, so Spring hands out a
    // proxy whose own fields are always null; reading `service.users` directly from another class fails.
    public UserRepository users() { return users; }
    public StudentRepository students() { return students; }
    public CompanyRepository companies() { return companies; }
    public DriveRepository drives() { return drives; }
    public ApplicationRepository applications() { return applications; }

    Student current(String email) {
        return students.findByUserEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student profile not found"));
    }

    <T> T get(org.springframework.data.jpa.repository.JpaRepository<T, Long> repo, Long id, String label) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found"));
    }

    Student register(RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.findByEmail(email).isPresent() || students.existsByRollNumber(r.rollNumber()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email or roll number already exists");
        User u = new User();
        u.email = email;
        u.password = encoder.encode(r.password());
        u.role = Role.ROLE_STUDENT;
        users.save(u);
        Student s = new Student();
        s.user = u;
        s.fullName = r.fullName();
        s.rollNumber = r.rollNumber();
        s.branch = r.branch();
        s.year = r.year();
        s.cgpa = r.cgpa();
        s.phone = r.phone();
        return students.save(s);
    }

    Set<Skill> skillSet(Set<String> names) {
        if (names == null)
            return new HashSet<>();
        return names.stream().filter(n -> n != null && !n.isBlank())
                .map(n -> skills.findByNameIgnoreCase(n.trim()).orElseGet(() -> {
                    Skill x = new Skill();
                    x.name = n.trim();
                    return skills.save(x);
                })).collect(Collectors.toSet());
    }

    Student updateStudent(String email, StudentRequest r) {
        Student s = current(email);
        if (r.fullName() != null)
            s.fullName = r.fullName();
        if (r.branch() != null)
            s.branch = r.branch();
        if (r.year() != null)
            s.year = r.year();
        if (r.cgpa() != null) {
            if (r.cgpa().compareTo(BigDecimal.ZERO) < 0 || r.cgpa().compareTo(BigDecimal.TEN) > 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CGPA must be between 0 and 10");
            s.cgpa = r.cgpa();
        }
        if (r.phone() != null)
            s.phone = r.phone();
        if (r.skills() != null)
            s.skills = skillSet(r.skills());
        return students.save(s);
    }

    Company company(CompanyRequest r) {
        Company c = new Company();
        copy(c, r);
        return companies.save(c);
    }

    void copy(Company c, CompanyRequest r) {
        c.name = r.name();
        c.description = r.description();
        c.website = r.website();
        c.industry = r.industry();
        c.location = r.location();
    }

    Map<String, Object> companyView(Company c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id); m.put("name", c.name); m.put("description", c.description);
        m.put("website", c.website); m.put("industry", c.industry); m.put("location", c.location);
        return m;
    }

    @Transactional
    void deleteCompany(Long id) {
        Company c = get(companies, id, "Company");
        List<PlacementDrive> companyDrives = drives.findByCompanyId(id);
        for (PlacementDrive d : companyDrives) applications.deleteByDriveId(d.id);
        drives.deleteAll(companyDrives);
        companies.delete(c);
    }

    @Transactional
    void deleteDrive(Long id) {
        get(drives, id, "Drive");
        applications.deleteByDriveId(id);
        drives.deleteById(id);
    }

    PlacementDrive drive(DriveRequest r) {
        PlacementDrive d = new PlacementDrive();
        copy(d, r);
        return drives.save(d);
    }

    void copy(PlacementDrive d, DriveRequest r) {
        d.company = get(companies, r.companyId(), "Company");
        d.jobTitle = r.jobTitle();
        d.jobDescription = r.jobDescription();
        d.salaryPackage = r.salaryPackage();
        d.jobLocation = r.jobLocation();
        d.driveDate = r.driveDate();
        d.applicationDeadline = r.applicationDeadline();
        d.minimumCgpa = r.minimumCgpa();
        d.eligibleBranches = r.eligibleBranches();
        d.jobType = r.jobType();
        d.requiredSkills = skillSet(r.requiredSkills());
        if (r.active() != null)
            d.active = r.active();
    }

    EligibilityResponse eligibility(Student s, PlacementDrive d) {
        List<String> why = new ArrayList<>();
        if (s.cgpa.compareTo(d.minimumCgpa) < 0)
            why.add("Minimum CGPA: " + d.minimumCgpa + "; yours: " + s.cgpa);
        Set<String> branches = Arrays.stream(Optional.ofNullable(d.eligibleBranches).orElse("").split(","))
                .map(x -> x.trim().toLowerCase()).filter(x -> !x.isBlank()).collect(Collectors.toSet());
        if (!branches.isEmpty() && !branches.contains(s.branch.toLowerCase()))
            why.add("Your branch (" + s.branch + ") is not eligible");
        Set<String> mine = s.skills.stream().map(x -> x.name.toLowerCase()).collect(Collectors.toSet());
        List<String> missing = d.requiredSkills.stream().map(x -> x.name).filter(x -> !mine.contains(x.toLowerCase()))
                .toList();
        if (!missing.isEmpty())
            why.add("Missing skills: " + String.join(", ", missing));
        if (!d.active)
            why.add("This drive is inactive");
        if (d.applicationDeadline.isBefore(LocalDate.now()))
            why.add("Application deadline has passed");
        return new EligibilityResponse(why.isEmpty(), why);
    }

    Application apply(String email, Long driveId) {
        Student s = current(email);
        PlacementDrive d = get(drives, driveId, "Drive");
        if (applications.existsByStudentIdAndDriveId(s.id, d.id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already applied");
        EligibilityResponse e = eligibility(s, d);
        if (!e.eligible())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", e.reasons()));
        Application a = new Application();
        a.student = s;
        a.drive = d;
        return applications.save(a);
    }

    Map<String, Object> dashboard() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalStudents", students.count());
        m.put("totalCompanies", companies.count());
        m.put("activeDrives", drives.countByActiveTrue());
        m.put("totalApplications", applications.count());
        m.put("selectedStudents", applications.countByStatus(ApplicationStatus.SELECTED));
        m.put("applicationsByStatus", Arrays.stream(ApplicationStatus.values())
                .collect(Collectors.toMap(Enum::name, applications::countByStatus, (a, b) -> a, LinkedHashMap::new)));
        return m;
    }

    Map<String, Object> studentView(Student s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("fullName", s.fullName);
        m.put("email", s.user.email);
        m.put("rollNumber", s.rollNumber);
        m.put("branch", s.branch);
        m.put("year", s.year);
        m.put("cgpa", s.cgpa);
        m.put("phone", s.phone);
        m.put("skills", s.skills.stream().map(x -> x.name).sorted().toList());
        return m;
    }

    Map<String, Object> driveView(PlacementDrive d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.id);
        m.put("company", Map.of("id", d.company.id, "name", d.company.name));
        m.put("jobTitle", d.jobTitle);
        m.put("jobDescription", d.jobDescription);
        m.put("salaryPackage", d.salaryPackage);
        m.put("jobLocation", d.jobLocation);
        m.put("driveDate", d.driveDate);
        m.put("applicationDeadline", d.applicationDeadline);
        m.put("minimumCgpa", d.minimumCgpa);
        m.put("eligibleBranches", d.eligibleBranches);
        m.put("jobType", d.jobType);
        m.put("active", d.active);
        m.put("requiredSkills", d.requiredSkills.stream().map(x -> x.name).toList());
        return m;
    }

    Map<String, Object> applicationView(Application a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put("student", studentView(a.student));
        m.put("drive", driveView(a.drive));
        m.put("appliedDate", a.appliedDate);
        m.put("status", a.status);
        return m;
    }
}
