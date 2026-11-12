package com.dth.frw.domain.school.student;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.TenantOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A student of one school: the person, who outlives any one school year. Which
 * class they sit in, and when, is a {@link StudentEnrollment}.
 *
 * <p>Not a row-level resource of its own: it is reached only through its
 * enrollments, which are, so the school boundary on those is the boundary here.
 * The student code is unique within the school.
 */
@Entity
@Table(
        name = "students",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_students_organization_code",
                columnNames = {"organization_id", "student_code"}))
public class Student implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "student_code", nullable = false, length = 100)
    private String studentCode;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column(name = "personal_id", length = 50)
    private String personalId;

    @Column(name = "passport_number", length = 50)
    private String passportNumber;

    @Column(name = "statistic_code", length = 100)
    private String statisticCode;

    @Column(name = "admission_year")
    private Integer admissionYear;

    @Column(name = "health_insurance_code", length = 50)
    private String healthInsuranceCode;

    private String province;
    private String district;
    private String ward;
    private String area;

    @Column(name = "house_number", length = 100)
    private String houseNumber;

    private String hamlet;

    @Column(length = 1000)
    private String address;

    @Column(name = "guardian_name")
    private String guardianName;

    @Column(name = "guardian_phone", length = 50)
    private String guardianPhone;

    @Column(name = "guardian_email")
    private String guardianEmail;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account_holder")
    private String bankAccountHolder;

    @Column(name = "bank_account_number", length = 100)
    private String bankAccountNumber;

    @Column(name = "needs_invoice", nullable = false)
    private boolean needsInvoice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Student() {
        // for JPA
    }

    public Student(Organization organization, String studentCode, String fullName) {
        this.organization = organization;
        this.studentCode = studentCode;
        this.fullName = fullName;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public Organization getOrganization() {
        return organization;
    }

    public Long getId() {
        return id;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public void setStudentCode(String studentCode) {
        this.studentCode = studentCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getPersonalId() {
        return personalId;
    }

    public void setPersonalId(String personalId) {
        this.personalId = personalId;
    }

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String passportNumber) {
        this.passportNumber = passportNumber;
    }

    public String getStatisticCode() {
        return statisticCode;
    }

    public void setStatisticCode(String statisticCode) {
        this.statisticCode = statisticCode;
    }

    public Integer getAdmissionYear() {
        return admissionYear;
    }

    public void setAdmissionYear(Integer admissionYear) {
        this.admissionYear = admissionYear;
    }

    public String getHealthInsuranceCode() {
        return healthInsuranceCode;
    }

    public void setHealthInsuranceCode(String healthInsuranceCode) {
        this.healthInsuranceCode = healthInsuranceCode;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getHouseNumber() {
        return houseNumber;
    }

    public void setHouseNumber(String houseNumber) {
        this.houseNumber = houseNumber;
    }

    public String getHamlet() {
        return hamlet;
    }

    public void setHamlet(String hamlet) {
        this.hamlet = hamlet;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getGuardianName() {
        return guardianName;
    }

    public void setGuardianName(String guardianName) {
        this.guardianName = guardianName;
    }

    public String getGuardianPhone() {
        return guardianPhone;
    }

    public void setGuardianPhone(String guardianPhone) {
        this.guardianPhone = guardianPhone;
    }

    public String getGuardianEmail() {
        return guardianEmail;
    }

    public void setGuardianEmail(String guardianEmail) {
        this.guardianEmail = guardianEmail;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankAccountHolder() {
        return bankAccountHolder;
    }

    public void setBankAccountHolder(String bankAccountHolder) {
        this.bankAccountHolder = bankAccountHolder;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public boolean isNeedsInvoice() {
        return needsInvoice;
    }

    public void setNeedsInvoice(boolean needsInvoice) {
        this.needsInvoice = needsInvoice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Student that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
