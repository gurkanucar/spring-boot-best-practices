package com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Runway {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "airport_code")
    private Airport airport;

    @Column(nullable = false, length = 10)
    private String designator;

    @Column(name = "length_meters", nullable = false)
    private int lengthMeters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunwaySurface surface;

    Runway(Airport airport, String designator, int lengthMeters, RunwaySurface surface) {
        this.airport = airport;
        this.designator = designator;
        update(lengthMeters, surface);
    }

    void update(int lengthMeters, RunwaySurface surface) {
        this.lengthMeters = lengthMeters;
        this.surface = surface;
    }
}
