package com.placement.pms;

import jakarta.persistence.*; import jakarta.validation.constraints.*; import java.math.*; import java.time.*; import java.util.*;

enum Role { ROLE_STUDENT, ROLE_ADMIN }
enum ApplicationStatus { APPLIED, SHORTLISTED, INTERVIEW, SELECTED, REJECTED }

@Entity @Table(name="users") class User {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(unique=true,nullable=false) String email; @Column(nullable=false) String password; @Enumerated(EnumType.STRING) Role role;
}
@Entity class Student {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @OneToOne(optional=false) User user; @Column(unique=true,nullable=false) String rollNumber; String fullName; String branch; Integer year; BigDecimal cgpa; String phone;
 @ManyToMany(cascade={CascadeType.PERSIST,CascadeType.MERGE}) @JoinTable(name="student_skills") Set<Skill> skills=new HashSet<>();
}
@Entity class Skill { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(unique=true) String name; }
@Entity class Company { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(nullable=false) String name; @Column(length=2000) String description; String website; String industry; String location; }
@Entity @Table(name="placement_drives") class PlacementDrive {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @ManyToOne(optional=false) Company company; String jobTitle; @Column(length=3000) String jobDescription; String salaryPackage; String jobLocation; LocalDate driveDate; LocalDate applicationDeadline; BigDecimal minimumCgpa; String eligibleBranches; String jobType; boolean active=true;
 @ManyToMany(cascade={CascadeType.PERSIST,CascadeType.MERGE}) @JoinTable(name="drive_skills") Set<Skill> requiredSkills=new HashSet<>();
}
@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames={"student_id","drive_id"})) class Application {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @ManyToOne(optional=false) Student student; @ManyToOne(optional=false) PlacementDrive drive; LocalDate appliedDate=LocalDate.now(); @Enumerated(EnumType.STRING) ApplicationStatus status=ApplicationStatus.APPLIED;
}

record RegisterRequest(@NotBlank String fullName,@Email @NotBlank String email,@NotBlank @Size(min=6) String password,@NotBlank String rollNumber,@NotBlank String branch,@NotNull @Min(1) @Max(6) Integer year,@NotNull @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal cgpa,@NotBlank String phone) {}
record LoginRequest(@Email @NotBlank String email,@NotBlank String password) {}
record AuthResponse(String token,String role,String name) {}
record StudentRequest(String fullName,String branch,Integer year,BigDecimal cgpa,String phone,Set<String> skills) {}
record CompanyRequest(@NotBlank String name,String description,String website,String industry,String location) {}
record DriveRequest(@NotNull Long companyId,@NotBlank String jobTitle,String jobDescription,String salaryPackage,String jobLocation,@NotNull LocalDate driveDate,@NotNull LocalDate applicationDeadline,@NotNull @DecimalMin("0.0") BigDecimal minimumCgpa,String eligibleBranches,String jobType,Set<String> requiredSkills,Boolean active) {}
record StatusRequest(@NotNull ApplicationStatus status) {}
record EligibilityResponse(boolean eligible,List<String> reasons) {}
