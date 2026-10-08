package com.placement.pms;
import org.springframework.data.jpa.repository.*; import java.util.*;
interface UserRepository extends JpaRepository<User,Long>{ Optional<User> findByEmail(String email); }
interface StudentRepository extends JpaRepository<Student,Long>{ Optional<Student> findByUserEmail(String email); boolean existsByRollNumber(String rollNumber); long countByBranch(String branch); }
interface SkillRepository extends JpaRepository<Skill,Long>{ Optional<Skill> findByNameIgnoreCase(String name); }
interface CompanyRepository extends JpaRepository<Company,Long>{}
interface DriveRepository extends JpaRepository<PlacementDrive,Long>{ List<PlacementDrive> findByActiveTrue(); List<PlacementDrive> findByCompanyId(Long companyId); long countByActiveTrue(); }
interface ApplicationRepository extends JpaRepository<Application,Long>{ List<Application> findByStudentUserEmailOrderByAppliedDateDesc(String email); List<Application> findByDriveId(Long driveId); boolean existsByStudentIdAndDriveId(Long studentId,Long driveId); long countByStatus(ApplicationStatus status); void deleteByDriveId(Long driveId); }
