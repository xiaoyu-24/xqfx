package com.xqfx.requirements.system;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "systems")
public class SystemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String ownerName;

    @ElementCollection
    @CollectionTable(name = "system_collaborators", joinColumns = @JoinColumn(name = "system_id"))
    @Column(name = "collaborator_name", nullable = false, length = 50)
    private List<String> collaborators = new ArrayList<>();

    protected SystemEntity() {
    }

    public SystemEntity(SystemProfile profile) {
        this.name = profile.name();
        this.ownerName = profile.ownerName();
        this.collaborators = new ArrayList<>(profile.collaborators());
    }

    public Long id() {
        return id;
    }

    String name() {
        return name;
    }

    String ownerName() {
        return ownerName;
    }

    List<String> collaborators() {
        return List.copyOf(collaborators);
    }
}
