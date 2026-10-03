package com.onpoom.remoteupdate.project;

import com.onpoom.remoteupdate.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "project")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    public static Project create(String name, String description) {
        Project project = new Project();
        project.name = name;
        project.description = description;
        return project;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
